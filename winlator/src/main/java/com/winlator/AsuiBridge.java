package com.winlator;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.winlator.box64.Box64Preset;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.container.Drive;
import com.winlator.container.GraphicsDrivers;
import com.winlator.core.Callback;
import com.winlator.core.WineThemeManager;
import com.winlator.xenvironment.RootFS;
import com.winlator.xenvironment.RootFSInstaller;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;

/** ASUI (Kotlin) tarafinin Winlator motorunu cagirdigi tek giris noktasi. */
public final class AsuiBridge {
    private static final String DRIVE_LETTERS = "DEFGHIJKLMNOPQRSTUVWXYZ";

    private AsuiBridge() {}

    /** Rootfs (Wine, Box64, suruculer) kurulu ve guncel mi? */
    public static boolean isRuntimeInstalled(Context context) {
        RootFS rootFS = RootFS.find(context);
        return rootFS.isValid() && rootFS.getVersion() >= RootFSInstaller.LATEST_VERSION;
    }

    /** Ice aktarilan .exe dosyalarinin durdugu klasor (container'da surucu olarak baglanir). */
    public static File getProgramsDir(Context context) {
        File dir = new File(new File(context.getFilesDir(), "winlator-runtime"), "programs");
        if (!dir.isDirectory()) dir.mkdirs();
        return dir;
    }

    /** Ilk kurulum: Winlator ekrani rootfs'i cikarir (izin ister, ilerleme gosterir). */
    public static Intent createSetupIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }
    
public static String getProgramsDriveLetter(Context context) {
    ContainerManager manager = new ContainerManager(context);
    ArrayList<Container> containers = manager.getContainers();
    if (containers.isEmpty()) return "D";
    Container container = containers.get(0);
    String programsPath = getProgramsDir(context).getPath();
    for (Drive drive : container.drivesIterator()) {
        if (drive.path.equals(programsPath)) {
            return drive.letter;
        }
    }
    return "D";
}

public static Intent createRunIntent(Context context, int containerId, String exeName) {
    Intent intent = new Intent(context, XServerDisplayActivity.class);
    intent.putExtra("container_id", containerId);
    
    // Android yolunu Windows yoluna çevir (Örn: D:\snake.exe)
    String drive = getProgramsDriveLetter(context);
    intent.putExtra("exec_path", drive + ":\\" + exeName);

    intent.putExtra("work_dir", drive + ":\\");

    intent.putExtra("debug", true);
    
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    return intent;
}

    

    /**
     * Var olan ilk container'i dondurur, yoksa varsayilan ayarlarla olusturur.
     * ANA IPLIKTEN cagrilmali; callback ana iplikte gelir. Basarisizsa 0 doner.
     */
    public static void prepareContainer(final Context context, final Callback<Integer> callback) {
        final ContainerManager manager = new ContainerManager(context);
        ArrayList<Container> containers = manager.getContainers();
        if (!containers.isEmpty()) {
            Container existing = containers.get(0);
            ensureProgramsDrive(context, existing);
            callback.call(existing.id);
            return;
        }

        JSONObject data = new JSONObject();
        try {
            data.put("name", "ASUI");
            data.put("screenSize", "800x600");
            data.put("graphicsDriver", "vortek");
            data.put("envVars", Container.DEFAULT_ENV_VARS);
            data.put("cpuList", Container.getFallbackCPUList());
            data.put("cpuListWoW64", Container.getFallbackCPUList());

            data.put("graphicsDriver", "turnip,virgl");
            data.put("dxwrapper", "dxvk");
            data.put("dxwrapperConfig", "");
            data.put("graphicsDriverConfig", "");
            data.put("audioDriver", Container.DEFAULT_AUDIO_DRIVER);
            data.put("audioDriverConfig", "");
            data.put("wincomponents", Container.DEFAULT_WINCOMPONENTS);
            data.put("drives", Container.DEFAULT_DRIVES);
            data.put("hudMode", 0);
            data.put("startupSelection", (int)Container.STARTUP_SELECTION_ESSENTIAL);
            data.put("box64Preset", Box64Preset.DEFAULT);
            data.put("desktopTheme", WineThemeManager.DEFAULT_DESKTOP_THEME);
        }
        catch (JSONException e) {
            callback.call(0);
            return;
        }

        manager.createContainerAsync(data, container -> {
            if (container == null) {
                callback.call(0);
                return;
            }
            ensureProgramsDrive(context, container);
            callback.call(container.id);
        });
    }

    private static void ensureProgramsDrive(Context context, Container container) {
        String programsPath = getProgramsDir(context).getPath();
        StringBuilder used = new StringBuilder();
        int count = 0;
        for (Drive drive : container.drivesIterator()) {
            if (drive.path.equals(programsPath)) return;
            used.append(drive.letter);
            count++;
        }
        if (count >= Container.MAX_DRIVE_LETTERS) return;

        for (int i = 0; i < DRIVE_LETTERS.length(); i++) {
            String letter = String.valueOf(DRIVE_LETTERS.charAt(i));
            if (used.indexOf(letter) == -1) {
                container.setDrives(container.getDrives() + letter + ":" + programsPath);
                container.saveData();
                return;
            }
        }
    }
}
