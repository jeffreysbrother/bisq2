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

package bisq.network.p2p.services.confidential;

import bisq.network.p2p.message.NetworkMessage;
import bisq.network.p2p.services.data.storage.DistributedData;
import bisq.network.p2p.services.data.storage.MetaData;
import bisq.security.ConfidentialData;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode
@Getter
public class ConfidentialMessage implements NetworkMessage, DistributedData {
    private final ConfidentialData confidentialData;
    private final String keyId;

    public ConfidentialMessage(ConfidentialData confidentialData, String keyId) {
        this.confidentialData = confidentialData;
        this.keyId = keyId;
    }

    @Override
    public String toString() {
        return "ConfidentialMessage{" +
                "\r\n     sealed=" + confidentialData +
                "\r\n     keyId=" + keyId +
                "\r\n}";
    }

    @Override
    public MetaData getMetaData() {
        return null; //todo
    }

    @Override
    public boolean isDataInvalid() {
        return false;
    }
}
