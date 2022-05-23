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

package bisq.desktop.primary.main.content.trade.bisqEasy.onboarding;

import bisq.application.DefaultApplicationService;
import bisq.desktop.common.Browser;
import bisq.desktop.common.view.Controller;
import bisq.desktop.common.view.Navigation;
import bisq.desktop.common.view.NavigationTarget;
import bisq.i18n.Res;
import bisq.settings.CookieKey;
import bisq.settings.SettingsService;
import lombok.Getter;

public class BisqEasyOnBoardingController implements Controller {
    private final BisqEasyOnBoardingModel model;
    @Getter
    private final BisqEasyOnBoardingView view;
    private final SettingsService settingsService;

    public BisqEasyOnBoardingController(DefaultApplicationService applicationService) {
        settingsService = applicationService.getSettingsService();
        model = new BisqEasyOnBoardingModel();
        view = new BisqEasyOnBoardingView(model, this);
    }

    @Override
    public void onActivate() {
        model.getIndex().set(1);
        model.getHeadline().set(Res.get("bisqEasy.onBoarding.part1.headline"));
        model.getText().set(Res.get("bisqEasy.onBoarding.part1.text"));
    }

    @Override
    public void onDeactivate() {
    }

    public void onLearnMore() {
        Browser.open("https://bisq.wiki");
    }

    public void onNext() {
        int index = model.getIndex().get() + 1;
        if (index <= model.getLastIndex()) {
            model.getIndex().set(index);
            model.getHeadline().set(Res.get("bisqEasy.onBoarding.part" + index + ".headline"));
            model.getText().set(Res.get("bisqEasy.onBoarding.part" + index + ".text"));
        } else {
            settingsService.setCookie(CookieKey.BISQ_EASY_ONBOARDED, true);
            Navigation.navigateTo(NavigationTarget.BISQ_EASY_CHAT);
        }
    }
}