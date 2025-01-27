/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.network.p2p.services.confidential.resend;

import bisq.common.observable.Observable;
import bisq.common.observable.Pin;
import bisq.common.observable.map.HashMapObserver;
import bisq.common.timer.Scheduler;
import bisq.network.NetworkService;
import bisq.network.identity.NetworkIdWithKeyPair;
import bisq.network.p2p.node.Node;
import bisq.network.p2p.services.confidential.ack.MessageDeliveryStatus;
import bisq.network.p2p.services.confidential.ack.MessageDeliveryStatusService;
import bisq.persistence.DbSubDirectory;
import bisq.persistence.Persistence;
import bisq.persistence.PersistenceClient;
import bisq.persistence.PersistenceService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Getter
public class ResendMessageService implements PersistenceClient<ResendMessageStore> {
    private static final long RESEND_INTERVAL = TimeUnit.MINUTES.toMillis(2);
    private static final int MAX_AUTO_RESENDS = 2;
    private static final int MAX_MANUAL_RESENDS = 3;

    private final ResendMessageStore persistableStore = new ResendMessageStore();
    private final Persistence<ResendMessageStore> persistence;
    private final NetworkService networkService;
    private final MessageDeliveryStatusService messageDeliveryStatusService;
    private final Map<String, Pin> messageDeliveryStatusPinByMessageId = new HashMap<>();
    private final Map<String, Scheduler> schedulerByMessageId = new HashMap<>();
    private final Set<Pin> nodeStatePins = new HashSet<>();
    private Pin messageDeliveryStatusByMessageIdPin;
    private volatile boolean isShutdown;

    public ResendMessageService(PersistenceService persistenceService,
                                NetworkService networkService,
                                MessageDeliveryStatusService messageDeliveryStatusService) {

        persistence = persistenceService.getOrCreatePersistence(this, DbSubDirectory.PRIVATE, persistableStore);
        this.networkService = networkService;
        this.messageDeliveryStatusService = messageDeliveryStatusService;
    }

    @Override
    public ResendMessageStore prunePersisted(ResendMessageStore persisted) {
        return new ResendMessageStore(persisted.getResendMessageDataByMessageId().entrySet().stream()
                .filter(e -> !e.getValue().isExpired())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)),
                persisted.getNumResendsByMessageId());
    }

    public void initialize() {
        messageDeliveryStatusByMessageIdPin = messageDeliveryStatusService.getMessageDeliveryStatusByMessageId().addObserver(
                new HashMapObserver<>() {
                    @Override
                    public void put(String messageId, Observable<MessageDeliveryStatus> status) {
                        handleMessageDeliveryStatusUpdate(messageId, status);
                    }

                    @Override
                    public void putAll(Map<? extends String, ? extends Observable<MessageDeliveryStatus>> map) {
                        map.forEach((key, value) -> handleMessageDeliveryStatusUpdate(key, value));
                    }

                    @Override
                    public void remove(Object messageId) {
                    }

                    @Override
                    public void clear() {
                    }
                });

        networkService.getDefaultNodeStateByTransportType().forEach((key, value) -> {
            nodeStatePins.add(value.addObserver(state -> {
                if (state == Node.State.RUNNING) {
                    // We get messages with CONNECTING, SENT or TRY_ADD_TO_MAILBOX converted to FAILED and thus
                    // get a resend triggered. After 1 sec after init we get the state update and a 15 sec scheduler
                    // for resend gets started. We delay here 10 sec. and filter out those which got already scheduled.
                    // For messages which have been in FAILED and no change got triggered we run the resend if the
                    // MAX_RESENDS has not got exceeded.
                    Scheduler.run(this::resendMessageAllFailedMessages).after(10, TimeUnit.SECONDS);
                }
            }));
        });
    }

    public void shutdown() {
        if (isShutdown) {
            return;
        }
        isShutdown = true;
        if (messageDeliveryStatusByMessageIdPin != null) {
            messageDeliveryStatusByMessageIdPin.unbind();
        }
        messageDeliveryStatusPinByMessageId.values().forEach(Pin::unbind);
        messageDeliveryStatusPinByMessageId.clear();
        nodeStatePins.forEach(Pin::unbind);
        // Clone to avoid ConcurrentModificationException
        List<Scheduler> schedulers = new ArrayList<>(schedulerByMessageId.values());
        schedulers.forEach(Scheduler::stop);
        schedulerByMessageId.clear();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////////////

    public void registerResendMessageData(ResendMessageData resendMessageData) {
        if (isShutdown) {
            return;
        }
        MessageDeliveryStatus messageDeliveryStatus = resendMessageData.getMessageDeliveryStatus();
        String messageId = resendMessageData.getId();
        Map<String, ResendMessageData> resendMessageDataByMessageId = persistableStore.getResendMessageDataByMessageId();
        switch (messageDeliveryStatus) {
            case CONNECTING:
            case SENT:
            case TRY_ADD_TO_MAILBOX:
                resendMessageDataByMessageId.put(messageId, resendMessageData);
                persist();
                restartResendTimer(resendMessageData, RESEND_INTERVAL);
                break;
            case FAILED:
                resendMessageDataByMessageId.put(messageId, resendMessageData);
                persist();
                restartResendTimer(resendMessageData, TimeUnit.SECONDS.toMillis(15));
                break;

            // We will not get the following states from the client, but we handle it in case client code changes later
            case ACK_RECEIVED:
            case ADDED_TO_MAILBOX:
            case MAILBOX_MSG_RECEIVED:
                resendMessageDataByMessageId.remove(messageId);
                persistableStore.getNumResendsByMessageId().remove(messageId);
                if (messageDeliveryStatusPinByMessageId.containsKey(messageId)) {
                    messageDeliveryStatusPinByMessageId.get(messageId).unbind();
                    messageDeliveryStatusPinByMessageId.remove(messageId);
                }
                persist();
                stopResendTimer(resendMessageData);
                break;
        }
        persist();
    }

    public void manuallyResendMessage(String messageId) {
        if (isShutdown) {
            return;
        }
        findResendMessageData(messageId).ifPresent(data -> resendMessage(data, MAX_MANUAL_RESENDS));
    }

    public boolean canManuallyResendMessage(String messageId) {
        return Optional.ofNullable(persistableStore.getNumResendsByMessageId().get(messageId))
                .map(AtomicInteger::get)
                .orElse(0) <= MAX_MANUAL_RESENDS &&
                findResendMessageData(messageId).isPresent();
    }

    public Set<ResendMessageData> getPendingResendMessageDataSet() {
        return persistableStore.getResendMessageDataByMessageId().values().stream()
                .filter(e -> e.getMessageDeliveryStatus().isPending())
                .collect(Collectors.toSet());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////////////

    private void resendMessage(ResendMessageData data, int maxResends) {
        String messageId = data.getId();
        persistableStore.getNumResendsByMessageId().putIfAbsent(messageId, new AtomicInteger(1));
        AtomicInteger numResends = persistableStore.getNumResendsByMessageId().get(messageId);
        if (numResends.get() > maxResends) {
            log.warn("Do not resend message with ID {} because we have already sent {} times", messageId, maxResends);
            return;
        } else {
            numResends.getAndIncrement();
        }
        persist();

        log.info("Resending message with ID {}; status={}", messageId, data.getMessageDeliveryStatus());
        NetworkIdWithKeyPair senderNetworkIdWithKeyPair = new NetworkIdWithKeyPair(data.getSenderNetworkId(), data.getSenderKeyPair());
        networkService.confidentialSend(data.getEnvelopePayloadMessage(),
                data.getReceiverNetworkId(),
                senderNetworkIdWithKeyPair);
    }

    private void handleMessageDeliveryStatusUpdate(String messageId,
                                                   Observable<MessageDeliveryStatus> messageDeliveryStatus) {
        findResendMessageData(messageId).ifPresent(resendMessageData -> {
            Optional.ofNullable(messageDeliveryStatusPinByMessageId.get(messageId)).ifPresent(Pin::unbind);
            Pin pin = messageDeliveryStatus.addObserver(status -> {
                Map<String, ResendMessageData> resendMessageDataByMessageId = persistableStore.getResendMessageDataByMessageId();
                ResendMessageData updatedResendMessageData = ResendMessageData.from(resendMessageData, status);
                switch (status) {
                    case CONNECTING:
                    case SENT:
                    case TRY_ADD_TO_MAILBOX:
                        break;

                    case FAILED:
                        persistableStore.getResendMessageDataByMessageId().put(messageId, updatedResendMessageData);
                        persist();
                        restartResendTimer(updatedResendMessageData, TimeUnit.SECONDS.toMillis(15));
                        break;

                    case ADDED_TO_MAILBOX:
                        // We get the ADDED_TO_MAILBOX state once we have broadcast successfully after the
                        // TRY_ADD_TO_MAILBOX state was set.
                        // We do not try to resend as it's in the mailbox system. Though we keep the data in case
                        // we want to resend later manually in case the peer never received it.
                        resendMessageDataByMessageId.put(messageId, updatedResendMessageData);
                        persist();
                        stopResendTimer(updatedResendMessageData);
                        break;
                    case ACK_RECEIVED:
                    case MAILBOX_MSG_RECEIVED:
                        persistableStore.getResendMessageDataByMessageId().remove(messageId);
                        persistableStore.getNumResendsByMessageId().remove(messageId);
                        persist();
                        stopResendTimer(updatedResendMessageData);

                        Optional.ofNullable(messageDeliveryStatusPinByMessageId.get(messageId)).ifPresent(Pin::unbind);
                        messageDeliveryStatusPinByMessageId.remove(messageId);
                        break;
                }
            });
            messageDeliveryStatusPinByMessageId.put(messageId, pin);
        });
    }

    private Optional<ResendMessageData> findResendMessageData(String messageId) {
        return persistableStore.getResendMessageDataByMessageId().entrySet().stream()
                .filter(entry -> entry.getKey().equals(messageId))
                .filter(entry -> !entry.getValue().isExpired())
                .map(Map.Entry::getValue)
                .filter(data -> data.getId().equals(messageId))
                .findAny();
    }

    private void resendMessageAllFailedMessages() {
        if (isShutdown) {
            return;
        }
        persistableStore.getResendMessageDataByMessageId().values().stream()
                .filter(e -> !schedulerByMessageId.containsKey(e.getId())) // If we have a resend scheduled we skip it
                .filter(e -> !e.getMessageDeliveryStatus().isReceived() &&
                        e.getMessageDeliveryStatus() != MessageDeliveryStatus.ADDED_TO_MAILBOX)
                .forEach(data -> resendMessage(data, MAX_AUTO_RESENDS));
    }

    private void restartResendTimer(ResendMessageData resendMessageData, long interval) {
        if (isShutdown) {
            return;
        }
        String messageId = resendMessageData.getId();
        log.debug("restartResendTimer {}; status={}", messageId, resendMessageData.getMessageDeliveryStatus());
        if (schedulerByMessageId.containsKey(messageId)) {
            schedulerByMessageId.get(messageId).stop();
        }
        if (resendMessageData.getMessageDeliveryStatus().isReceived() ||
                resendMessageData.getMessageDeliveryStatus() == MessageDeliveryStatus.ADDED_TO_MAILBOX) {
            log.warn("We got called startResendTimer with an unexpected messageDeliveryStatus: {}",
                    resendMessageData.getMessageDeliveryStatus());
            return;
        }
        Scheduler scheduler = Scheduler.run(() -> resendMessage(resendMessageData, MAX_AUTO_RESENDS)).after(interval);
        schedulerByMessageId.put(messageId, scheduler);
    }

    private void stopResendTimer(ResendMessageData resendMessageData) {
        if (isShutdown) {
            return;
        }
        String messageId = resendMessageData.getId();
        if (schedulerByMessageId.containsKey(messageId)) {
            Scheduler scheduler = schedulerByMessageId.get(messageId);
            scheduler.stop();
            schedulerByMessageId.remove(messageId);
        }
    }
}
