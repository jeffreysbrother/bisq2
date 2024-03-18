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

package bisq.desktop.main.content.bisq_easy.trade_guide;

import bisq.bisq_easy.NavigationTarget;
import bisq.desktop.common.view.TabModel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class BisqEasyGuideModel extends TabModel {
    @Setter
    private boolean tradeRulesConfirmed;

    @Override
    public NavigationTarget getDefaultNavigationTarget() {
        return NavigationTarget.BISQ_EASY_GUIDE_WELCOME;
    }
}
