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

package bisq.desktop.main.content.chat.message_container.list.message_box;

import bisq.chat.ChatChannel;
import bisq.chat.ChatMessage;
import bisq.chat.Citation;
import bisq.chat.bisqeasy.offerbook.BisqEasyOfferbookMessage;
import bisq.chat.reactions.Reaction;
import bisq.desktop.common.utils.ClipboardUtil;
import bisq.desktop.common.utils.ImageUtil;
import bisq.desktop.components.controls.BisqMenuItem;
import bisq.desktop.components.controls.BisqTooltip;
import bisq.desktop.components.controls.DropdownMenu;
import bisq.desktop.main.content.chat.message_container.list.ChatMessageListItem;
import bisq.desktop.main.content.chat.message_container.list.ChatMessagesListController;
import bisq.desktop.main.content.chat.message_container.list.reactions_box.ActiveReactionsDisplayBox;
import bisq.desktop.main.content.chat.message_container.list.reactions_box.ReactMenuBox;
import bisq.desktop.main.content.chat.message_container.list.reactions_box.ToggleReaction;
import bisq.desktop.main.content.components.UserProfileIcon;
import bisq.i18n.Res;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Slf4j
public abstract class BubbleMessageBox extends MessageBox {
    private static final String HIGHLIGHTED_MESSAGE_BG_STYLE_CLASS = "highlighted-message-bg";
    protected static final double CHAT_MESSAGE_BOX_MAX_WIDTH = 630; // TODO: it should be 510 because of reactions on min size
    protected static final double OFFER_MESSAGE_USER_ICON_SIZE = 70;
    protected static final Insets ACTION_ITEMS_MARGIN = new Insets(2, 0, -2, 0);
    private static final List<Reaction> REACTIONS_ORDER = Arrays.asList(Reaction.THUMBS_UP, Reaction.THUMBS_DOWN, Reaction.HAPPY,
            Reaction.LAUGH, Reaction.HEART, Reaction.PARTY);

    private final Subscription showHighlightedPin;
    protected final ChatMessageListItem<? extends ChatMessage, ? extends ChatChannel<? extends ChatMessage>> item;
    protected final ListView<ChatMessageListItem<? extends ChatMessage, ? extends ChatChannel<? extends ChatMessage>>> list;
    protected final ChatMessagesListController controller;
    protected final UserProfileIcon userProfileIcon = new UserProfileIcon(60);
    protected final HBox actionsHBox = new HBox(5);
    protected final VBox quotedMessageVBox, contentVBox;
    private Subscription reactMenuPin;
    protected ActiveReactionsDisplayBox activeReactionsDisplayHBox;
    protected ReactMenuBox reactMenuBox;
    protected Label supportedLanguages, userName, dateTime, message;
    protected HBox userNameAndDateHBox, messageBgHBox, messageHBox;
    protected VBox userProfileIconVbox;
    protected BisqMenuItem copyAction;
    protected DropdownMenu moreActionsMenu;

    public BubbleMessageBox(ChatMessageListItem<? extends ChatMessage, ? extends ChatChannel<? extends ChatMessage>> item,
                            ListView<ChatMessageListItem<? extends ChatMessage, ? extends ChatChannel<? extends ChatMessage>>> list,
                            ChatMessagesListController controller) {
        this.item = item;
        this.list = list;
        this.controller = controller;

        setUpUserNameAndDateTime();
        setUpUserProfileIcon();
        setUpReactions();
        setUpActions();
        addActionsHandlers();
        addOnMouseEventHandlers();

        supportedLanguages = createAndGetSupportedLanguagesLabel();
        quotedMessageVBox = createAndGetQuotedMessageVBox();
        handleQuoteMessageBox();
        message = createAndGetMessage();
        messageBgHBox = createAndGetMessageBackground();
        messageHBox = createAndGetMessageBox();

        setFillWidth(true);
        HBox.setHgrow(this, Priority.ALWAYS);

        contentVBox = new VBox();
        contentVBox.setMaxWidth(CHAT_BOX_MAX_WIDTH);
        contentVBox.getStyleClass().add("chat-message-content-box");
        getChildren().setAll(contentVBox);
        setAlignment(Pos.CENTER);

        showHighlightedPin = EasyBind.subscribe(item.getShowHighlighted(), showHighlighted -> {
            if (showHighlighted) {
                messageBgHBox.getStyleClass().add(HIGHLIGHTED_MESSAGE_BG_STYLE_CLASS);
            } else {
                messageBgHBox.getStyleClass().remove(HIGHLIGHTED_MESSAGE_BG_STYLE_CLASS);
            }
        });
    }

    protected void setUpUserNameAndDateTime() {
        userName = new Label();
        userName.getStyleClass().addAll("text-fill-white", "font-size-09", "font-default");
        dateTime = new Label();
        dateTime.getStyleClass().addAll("text-fill-grey-dimmed", "font-size-09", "font-light");
        dateTime.setText(item.getDate());
        dateTime.setVisible(false);
    }

    private void setUpUserProfileIcon() {
        userProfileIcon.setSize(60);
        userProfileIconVbox = new VBox(userProfileIcon);

        item.getSenderUserProfile().ifPresent(author -> {
            userName.setText(author.getUserName());
            userName.setOnMouseClicked(e -> controller.onMention(author));

            userProfileIcon.setUserProfile(author);
            userProfileIcon.setCursor(Cursor.HAND);
            userProfileIcon.setOnMouseClicked(e -> controller.onShowChatUserDetails(item.getChatMessage()));
        });
    }

    private void setUpReactions() {
        // Active Reactions Display
        ToggleReaction toggleReactionDisplayMenuFunction = reactionItem ->
                controller.onReactMessage(item.getChatMessage(), reactionItem.getReaction(), item.getChatChannel());
        activeReactionsDisplayHBox = new ActiveReactionsDisplayBox(item.getUserReactions().values(), toggleReactionDisplayMenuFunction);

        // React Menu
        ToggleReaction toggleReactionReactMenuFunction = reactionItem -> {
            controller.onReactMessage(item.getChatMessage(), reactionItem.getReaction(), item.getChatChannel());
            reactMenuBox.hideMenu();
        };
        reactMenuBox = new ReactMenuBox(item.getUserReactions(), REACTIONS_ORDER, toggleReactionReactMenuFunction,
                "react-grey", "react-white", "react-green");
        reactMenuBox.setTooltip(Res.get("action.react"));
        reactMenuBox.setVisible(item.getChatMessage().canShowReactions());
        reactMenuBox.setManaged(item.getChatMessage().canShowReactions());

        reactMenuPin = EasyBind.subscribe(reactMenuBox.getIsMenuShowing(), isShowing -> {
            if (!isShowing && !isHover()) {
                showDateTimeAndActionsMenu(false);
            }
        });
    }

    protected void setUpActions() {
        copyAction = new BisqMenuItem("copy-grey", "copy-white");
        copyAction.useIconOnly();
        copyAction.setTooltip(Res.get("action.copyToClipboard"));
        actionsHBox.setVisible(false);
        HBox.setMargin(copyAction, ACTION_ITEMS_MARGIN);
        HBox.setMargin(reactMenuBox, ACTION_ITEMS_MARGIN);
    }

    protected void addActionsHandlers() {
    }

    private void addOnMouseEventHandlers() {
        setOnMouseEntered(e -> showDateTimeAndActionsMenu(true));
        setOnMouseExited(e -> showDateTimeAndActionsMenu(false));
    }

    @Override
    public void dispose() {
        setOnMouseEntered(null);
        setOnMouseExited(null);

        showHighlightedPin.unsubscribe();
        reactMenuPin.unsubscribe();

        activeReactionsDisplayHBox.dispose();
        reactMenuBox.dispose();
        userProfileIcon.dispose();
    }

    private void showDateTimeAndActionsMenu(boolean shouldShow) {
        if (shouldShow) {
            if ((moreActionsMenu != null && moreActionsMenu.getIsMenuShowing().get()) || reactMenuBox.getIsMenuShowing().get()) {
                return;
            }
            dateTime.setVisible(true);
            actionsHBox.setVisible(true);
        } else {
            if ((moreActionsMenu == null || !moreActionsMenu.getIsMenuShowing().get()) && !reactMenuBox.getIsMenuShowing().get()) {
                dateTime.setVisible(false);
                actionsHBox.setVisible(false);
            }
        }
    }

    private Label createAndGetSupportedLanguagesLabel() {
        Label label = new Label();
        if (item.isBisqEasyPublicChatMessageWithOffer()) {
            label.setGraphic(ImageUtil.getImageViewById("language-grey"));
            BisqEasyOfferbookMessage chatMessage = (BisqEasyOfferbookMessage) item.getChatMessage();
            label.setTooltip(new BisqTooltip(item.getSupportedLanguageCodesForTooltip(chatMessage)));
        }
        HBox.setMargin(label, new Insets(9, 0, -9, 0));
        return label;
    }

    private VBox createAndGetQuotedMessageVBox() {
        VBox vBox = new VBox(5);
        vBox.setVisible(false);
        vBox.setManaged(false);
        VBox.setMargin(vBox, new Insets(15, 0, 10, 5));
        return vBox;
    }

    private void handleQuoteMessageBox() {
        Optional<Citation> optionalCitation = item.getCitation();
        if (optionalCitation.isPresent()) {
            Citation citation = optionalCitation.get();
            if (citation.isValid()) {
                quotedMessageVBox.setVisible(true);
                quotedMessageVBox.setManaged(true);
                Label quotedMessageField = new Label();
                quotedMessageField.setWrapText(true);
                quotedMessageField.setText(citation.getText());
                quotedMessageField.setStyle("-fx-fill: -fx-mid-text-color");
                Label userName = new Label(controller.getUserName(citation.getAuthorUserProfileId()));
                userName.getStyleClass().add("font-medium");
                userName.setStyle("-fx-text-fill: -bisq-mid-grey-30");
                quotedMessageVBox.getChildren().setAll(userName, quotedMessageField);
            }
        } else {
            quotedMessageVBox.getChildren().clear();
            quotedMessageVBox.setVisible(false);
            quotedMessageVBox.setManaged(false);
        }
    }

    private Label createAndGetMessage() {
        Label label = new Label();
        label.maxWidthProperty().unbind();
        label.setWrapText(true);
        label.setPadding(new Insets(10));
        label.getStyleClass().addAll("text-fill-white", "medium-text", "font-default");
        label.setText(item.getMessage());
        return label;
    }

    private HBox createAndGetMessageBackground() {
        HBox hBox = new HBox(15);
        hBox.setAlignment(Pos.CENTER_LEFT);
        hBox.setMaxWidth(CHAT_MESSAGE_BOX_MAX_WIDTH);
        HBox.setHgrow(hBox, Priority.SOMETIMES);
        if (item.hasTradeChatOffer()) {
            hBox.setPadding(new Insets(15));
        } else {
            hBox.setPadding(new Insets(5, 15, 5, 15));
        }
        return hBox;
    }

    private HBox createAndGetMessageBox() {
        HBox hBox = new HBox(5);
        VBox.setMargin(hBox, new Insets(10, 0, 0, 0));
        return hBox;
    }

    protected static void onCopyMessage(ChatMessage chatMessage) {
        ClipboardUtil.copyToClipboard(chatMessage.getText());
    }

    protected static void onCopyMessage(String chatMessageText) {
        ClipboardUtil.copyToClipboard(chatMessageText);
    }
}
