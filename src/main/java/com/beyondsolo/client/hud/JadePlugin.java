package com.beyondsolo.client.hud;

import com.beyondsolo.BeyondSolo;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Our plugin for Jade, found by Jade itself when it is in the pack (nothing else loads this class). Jade's tooltip
 * makes room for the System's bars at the top of the screen: {@link JadeRoom}.
 */
@WailaPlugin(BeyondSolo.MOD_ID)
public final class JadePlugin implements IWailaPlugin {
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.addBeforeRenderCallback(JadeRoom::beforeRender);
    }
}
