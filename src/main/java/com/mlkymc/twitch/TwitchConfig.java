package com.mlkymc.twitch;

import com.mlkymc.config.MlkyConfig;

public class TwitchConfig {

    public boolean isEnabled() {
        return MlkyConfig.isTwitchEnabled()
                && !MlkyConfig.getTwitchClientId().isEmpty()
                && !MlkyConfig.getTwitchAccessToken().isEmpty()
                && !MlkyConfig.getTwitchRewardId().isEmpty();
    }

    public String getClientId() { return MlkyConfig.getTwitchClientId(); }
    public String getAccessToken() { return MlkyConfig.getTwitchAccessToken(); }
    public String getRewardId() { return MlkyConfig.getTwitchRewardId(); }
}
