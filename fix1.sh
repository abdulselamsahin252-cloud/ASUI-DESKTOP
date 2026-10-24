D=app/src/main/java/com/windowsui
[ -d "$D" ] || { echo "HATA: app klasörünün olduğu proje klasörüne gir, sonra tekrar çalıştır"; exit 1; }
cp -r app/src/main/java "$HOME/java_yedek_1" && echo "Yedek alındı: $HOME/java_yedek_1" || echo "UYARI: yedek alınamadı"
cd "$D" || exit 1

add_import() { grep -qxF "import $2" "$1" || sed -i "1a import $2" "$1"; }

sed -i 's/androidx\.compute\./androidx.compose./' FileExplorer.kt HiddenIconsPanel.kt
sed -i '/^import androidx\.compose\.Runtime$/d' FileExplorer.kt

sed -i '/^ *powerManager\.isPowerSaveMode$/,+3d' HardwareController.kt

sed -i 's/runCatching { HardwareController\.getCpuInfo() }/runCatching { kotlinx.coroutines.runBlocking { HardwareController.getCpuInfo() } }/' DeviceInfo.kt

sed -i '433s/window = window$/window = window,/' WindowComponent.kt
sed -i '405s/\.align(Alignment\.BottomEnd)/.align(Alignment.End)/' WindowComponent.kt
sed -i '/slideIn(initialOffset\|slideOut(targetOffset/s/animationSpec = animationSpec/animationSpec = tween(if (animationsEnabled) 300 else 0)/' WindowComponent.kt
sed -i '44{/^import androidx\.compose\.ui\.unit\.IntOffset$/d}' WindowComponent.kt

sed -i 's/^\( *\)var items = _allItems$/\1var items: List<DesktopGridItem> = _allItems/' DesktopGrid.kt
sed -i '/iconRes = when (type) {/,/^ *}$/c\                            iconRes = null' DesktopGrid.kt
sed -i 's/SystemState\.pinnedApps\.contains(it)) {/SystemState.pinnedApps.contains(it.packageName)) {/' DesktopGrid.kt
sed -i 's/SystemState\.pinnedApps\.any { pinned -> pinned\.packageName == it\.packageName }/SystemState.pinnedApps.contains(it.packageName)/' DesktopIcon.kt
sed -i 's/SystemState\.pinnedApps\.any { it\.packageName == item\.packageName }/SystemState.pinnedApps.contains(item.packageName)/' DesktopIcon.kt
sed -i 's/SystemState\.pinnedApps\.add(it)/SystemState.pinApp(context, it.packageName)/; s/TaskbarState\.pinApp(it)/TaskbarState.pinApp(context, it)/; s/SystemState\.pinnedApps\.remove(it)/SystemState.unpinApp(context, it.packageName)/; s/TaskbarState\.unpinApp(it)/TaskbarState.unpinApp(context, it)/' DesktopGrid.kt DesktopIcon.kt
add_import DesktopIcon.kt kotlin.math.roundToInt

sed -i '/ApplicationInfo\.CATEGORY_\(SHOPPING\|SPORTS\|HEALTH\|FINANCE\|EDUCATION\|BOOKS_AND_REFERENCE\|NAVIGATION\|WEATHER\|LAUNCHER\|BUSINESS\|MEDICAL\|DATING\|ENTERTAINMENT\|FOOD_AND_DRINK\|LIFESTYLE\|PERSONALIZATION\|PHOTOGRAPHY\) ->/d' DesktopManager.kt
sed -i 's/val categoryColor: Int/val categoryColor: Long/' DesktopManager.kt AppInfo.kt
sed -i '/^fun com\.windowsui\.core\.AppInfo\.toDesktopApp/,/^}/d' DesktopManager.kt

sed -i 's#^import androidx\.documentfile\.provider\.DocumentFile$#import androidx.compose.ui.geometry.Offset#' DesktopStorage.kt

sed -i 's/return@withContext TermuxBridge\.run(/return@withContext TermuxBridge.runCommandWithOutput(/; s/TermuxBridge\.run(context, termuxCommand/TermuxBridge.runCommand(context, termuxCommand/; s/WindowState\.virtualDesktops/SystemState.virtualDesktops/g; s/WindowState\.currentDesktop/SystemState.currentDesktop/g; s/DesktopState\.widgets\.add(widgetName)/DesktopState.addWidget(widgetName, context = context)/' CmdTerminal.kt

sed -i 's/TaskbarState\.unpinApp(app)$/TaskbarState.unpinApp(context, app)/' TaskbarComponent.kt
grep -q '^@file:OptIn' TaskbarComponent.kt || sed -i '1i @file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)' TaskbarComponent.kt

sed -i '235{/val packageName = event\.packageName/d}' WindowsAccessibilityService.kt
sed -i 's/it\.contentId == app\.packageName/it.contentId == app/; s/getLaunchIntentForPackage(app\.packageName)/getLaunchIntentForPackage(app)/' WindowsAccessibilityService.kt

sed -i 's/androidx\.lifecycle\.lifecycleScope\.launch/lifecycleScope.launch/' MainActivity.kt

sed -i 's/^\( *\)text( )$/\1text = "Cihazın ruh özelliği için gerekli izin.",/' SettingsWindow.kt
sed -i 's/Settings\.Global\.LOW_POWER_MODE) == 1/"low_power", 0) == 1/' SystemControllers.kt

sed -i 's/OutlinedButtonDefaults\.outlinedButtonColors/ButtonDefaults.outlinedButtonColors/; 211s#^\( *\)downloadApp(app)$#\1context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${app.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))#' StoreWindow.kt
sed -i 's/TextButtonDefaults\.textButtonColors/ButtonDefaults.textButtonColors/' LockScreenPanel.kt
add_import StartMenuPanel.kt androidx.compose.foundation.hoverable
add_import StartMenuPanel.kt androidx.compose.foundation.lazy.items
add_import StoreWindow.kt androidx.compose.foundation.lazy.items
add_import PersonalizationWindow.kt kotlinx.coroutines.delay
add_import NotificationPanel.kt androidx.compose.foundation.lazy.LazyRow

echo "Bitti. Şimdi derlemeyi tekrar çalıştır."
