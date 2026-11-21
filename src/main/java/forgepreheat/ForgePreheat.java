package forgepreheat;

import forgepreheat.profile.HitchSampler;
import forgepreheat.ui.JeiLoadingNotice;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(ForgePreheat.MOD_ID)
public final class ForgePreheat {
    public static final String MOD_ID = "forgepreheat";

    public ForgePreheat() {
        // Client-only mod; on a server it loads but does nothing.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientStartSession.begin();
            HitchSampler.install();
            JeiLoadingNotice.install();
        }
    }
}
