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

package bisq.desktop.main.content.common_chat;

import bisq.chat.channel.ChatChannel;
import bisq.chat.channel.ChatChannelDomain;
import bisq.chat.channel.ChatChannelSelectionService;
import bisq.chat.channel.priv.PrivateChatChannel;
import bisq.chat.channel.priv.TwoPartyPrivateChatChannel;
import bisq.chat.channel.pub.CommonPublicChatChannelService;
import bisq.chat.channel.pub.PublicChatChannel;
import bisq.chat.message.ChatMessage;
import bisq.common.observable.Pin;
import bisq.common.observable.collection.ObservableArray;
import bisq.desktop.ServiceProvider;
import bisq.desktop.common.threading.UIThread;
import bisq.desktop.common.view.Controller;
import bisq.desktop.common.view.NavigationTarget;
import bisq.desktop.components.controls.BisqIconButton;
import bisq.desktop.main.content.chat.ChatController;
import bisq.desktop.main.content.chat.channels.CommonPublicChannelSelectionMenu;
import bisq.desktop.main.content.chat.channels.PublicChannelSelectionMenu;
import bisq.desktop.main.content.chat.channels.TwoPartyPrivateChannelSelectionMenu;
import javafx.scene.control.Button;
import lombok.extern.slf4j.Slf4j;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import java.util.Optional;

@Slf4j
public class CommonChatController extends ChatController<CommonChatView, CommonChatModel> implements Controller {
    private ChatChannelSelectionService chatChannelSelectionService;
    private CommonPublicChatChannelService commonPublicChatChannelService;
    private PublicChannelSelectionMenu<?, ?, ?> publicChatChannelSelection;
    private TwoPartyPrivateChannelSelectionMenu twoPartyPrivateChannelSelectionMenu;
    private Pin selectedChannelPin, twoPartyPrivateChatChannelsPin;
    private Subscription searchTextPin;

    public CommonChatController(ServiceProvider serviceProvider, ChatChannelDomain chatChannelDomain) {
        super(serviceProvider, chatChannelDomain, NavigationTarget.NONE);
    }

    @Override
    public void createDependencies(ChatChannelDomain chatChannelDomain) {
        commonPublicChatChannelService = chatService.getCommonPublicChatChannelServices().get(chatChannelDomain);
        chatChannelSelectionService = chatService.getChatChannelSelectionServices().get(chatChannelDomain);
        publicChatChannelSelection = new CommonPublicChannelSelectionMenu(serviceProvider, chatChannelDomain);
        twoPartyPrivateChannelSelectionMenu = new TwoPartyPrivateChannelSelectionMenu(serviceProvider, chatChannelDomain);
    }

    @Override
    public CommonChatModel createAndGetModel(ChatChannelDomain chatChannelDomain) {
        return new CommonChatModel(chatChannelDomain);
    }

    @Override
    public CommonChatView createAndGetView() {
        return new CommonChatView(model,
                this,
                publicChatChannelSelection.getRoot(),
                twoPartyPrivateChannelSelectionMenu.getRoot(),
                chatMessagesComponent.getRoot(),
                channelSidebar.getRoot());
    }

    @Override
    public void onActivate() {
        model.getSearchText().set("");

        searchTextPin = EasyBind.subscribe(model.getSearchText(), searchText -> {
            if (searchText == null || searchText.isEmpty()) {
                chatMessagesComponent.setSearchPredicate(item -> true);
            } else {
                chatMessagesComponent.setSearchPredicate(item -> item.match(searchText));
            }
        });

        ObservableArray<TwoPartyPrivateChatChannel> twoPartyPrivateChatChannels = chatService.getTwoPartyPrivateChatChannelServices().get(model.getChatChannelDomain()).getChannels();
        twoPartyPrivateChatChannelsPin = twoPartyPrivateChatChannels.addListener(() ->
                model.getIsTwoPartyPrivateChatChannelSelectionVisible().set(!twoPartyPrivateChatChannels.isEmpty()));

        selectedChannelPin = chatChannelSelectionService.getSelectedChannel().addObserver(this::chatChannelChanged);
    }

    @Override
    public void onDeactivate() {
        searchTextPin.unsubscribe();
        twoPartyPrivateChatChannelsPin.unbind();
        selectedChannelPin.unbind();
    }

    @Override
    protected void chatChannelChanged(ChatChannel<? extends ChatMessage> chatChannel) {
        super.chatChannelChanged(chatChannel);

        UIThread.run(() -> {
            model.getSearchText().set("");
            if (chatChannel != null) {
                if (chatChannel instanceof TwoPartyPrivateChatChannel) {
                    applyPeersIcon((PrivateChatChannel<?>) chatChannel);
                    publicChatChannelSelection.deSelectChannel();
                } else {
                    applyDefaultPublicChannelIcon((PublicChatChannel<?>) chatChannel);
                    twoPartyPrivateChannelSelectionMenu.deSelectChannel();
                }
            }
        });
    }

    @Override
    protected Optional<? extends Controller> createController(NavigationTarget navigationTarget) {
        return Optional.empty();
    }

    private void applyDefaultPublicChannelIcon(PublicChatChannel<?> channel) {
        String iconId = "channels-" + channel.getId().replace(".", "-");
        Button iconButton = BisqIconButton.createIconButton(iconId);
        //todo get larger icons and dont use scaling
        iconButton.setScaleX(1.25);
        iconButton.setScaleY(1.25);
        model.getChannelIconNode().set(iconButton);
    }
}
