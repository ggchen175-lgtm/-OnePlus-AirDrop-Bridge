package dev.codex.moseyvpn;

import android.net.VpnService;
import java.util.ArrayList;
import java.util.List;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class MoseyVpnBypass implements IXposedHookLoadPackage {
    private static final String MOSEY = "com.google.android.mosey";
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam pkg) {
        if (!"com.sakura.app".equals(pkg.packageName)
                && !"com.github.metacubex.clash.meta".equals(pkg.packageName)
                && !"com.github.kr328.clash".equals(pkg.packageName)) return;
        XposedHelpers.findAndHookMethod(VpnService.Builder.class, "establish", new XC_MethodHook() {
            protected void beforeHookedMethod(MethodHookParam p) {
                try {
                    Object config = XposedHelpers.getObjectField(p.thisObject, "mConfig");
                    @SuppressWarnings("unchecked")
                    List<String> allowed = (List<String>) XposedHelpers.getObjectField(config, "allowedApplications");
                    if (allowed != null) {
                        if (allowed.contains(MOSEY)) {
                            // An empty allow-list may change VPN semantics. Preserve it rather than broadening access.
                            if (allowed.size() == 1) {
                                XposedBridge.log("CodexMoseyVPN: sole allowed app is Mosey; bypass skipped to preserve allow-list semantics");
                                return;
                            }
                            ArrayList<String> copy = new ArrayList<String>(allowed);
                            copy.remove(MOSEY);
                            XposedHelpers.setObjectField(config, "allowedApplications", copy);
                        }
                        XposedBridge.log("CodexMoseyVPN: Mosey excluded from VPN allow-list in " + pkg.processName);
                    } else {
                        ((VpnService.Builder) p.thisObject).addDisallowedApplication(MOSEY);
                        XposedBridge.log("CodexMoseyVPN: Mosey excluded from VPN in " + pkg.processName);
                    }
                } catch (Throwable t) {
                    XposedBridge.log("CodexMoseyVPN: bypass failed; preserving original VPN configuration: " + t);
                }
            }
        });
        XposedBridge.log("CodexMoseyVPN: establish hook ready in " + pkg.processName);
    }
}
