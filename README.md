name: Build APK

on:
  push:
    branches: [ main, master ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Rebuild full project from scratch (ignores whatever is currently in the repo)
        run: |
          rm -rf app build.gradle.kts settings.gradle.kts gradle.properties gradle local.properties
          
          mkdir -p "."
          cat > "build.gradle.kts" << 'EOF_ROOT_BUILD'
          plugins {
              id("com.android.application") version "8.5.0" apply false
              id("org.jetbrains.kotlin.android") version "1.9.24" apply false
          }
          
          EOF_ROOT_BUILD
          
          mkdir -p "."
          cat > "settings.gradle.kts" << 'EOF_SETTINGS'
          pluginManagement {
              repositories {
                  google()
                  mavenCentral()
                  gradlePluginPortal()
              }
          }
          dependencyResolutionManagement {
              repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
              repositories {
                  google()
                  mavenCentral()
              }
          }
          
          rootProject.name = "BilliardsTrajectoryAnalyzer"
          include(":app")
          
          EOF_SETTINGS
          
          mkdir -p "."
          cat > "gradle.properties" << 'EOF_GPROPS'
          org.gradle.jvmargs=-Xmx2048m
          android.useAndroidX=true
          kotlin.code.style=official
          android.nonTransitiveRClass=true
          
          EOF_GPROPS
          
          mkdir -p "gradle/wrapper"
          cat > "gradle/wrapper/gradle-wrapper.properties" << 'EOF_WRAPPER'
          distributionBase=GRADLE_USER_HOME
          distributionPath=wrapper/dists
          distributionUrl=https\://services.gradle.org/distributions/gradle-8.7-bin.zip
          zipStoreBase=GRADLE_USER_HOME
          zipStorePath=wrapper/dists
          
          EOF_WRAPPER
          
          mkdir -p "app"
          cat > "app/build.gradle.kts" << 'EOF_APP_BUILD'
          plugins {
              id("com.android.application")
              id("org.jetbrains.kotlin.android")
          }
          
          android {
              namespace = "com.billiards.analyzer"
              compileSdk = 34
          
              defaultConfig {
                  applicationId = "com.billiards.analyzer"
                  minSdk = 24
                  targetSdk = 34
                  versionCode = 1
                  versionName = "1.0"
              }
          
              buildTypes {
                  release {
                      isMinifyEnabled = false
                  }
              }
          
              compileOptions {
                  sourceCompatibility = JavaVersion.VERSION_17
                  targetCompatibility = JavaVersion.VERSION_17
              }
          
              kotlinOptions {
                  jvmTarget = "17"
              }
          
              buildFeatures {
                  viewBinding = false
              }
          }
          
          dependencies {
              implementation("androidx.core:core-ktx:1.13.1")
              implementation("androidx.appcompat:appcompat:1.7.0")
              implementation("com.google.android.material:material:1.11.0")
          }
          
          EOF_APP_BUILD
          
          mkdir -p "app/src/main"
          cat > "app/src/main/AndroidManifest.xml" << 'EOF_MANIFEST'
          <?xml version="1.0" encoding="utf-8"?>
          <manifest xmlns:android="http://schemas.android.com/apk/res/android">
          
              <application
                  android:allowBackup="true"
                  android:label="@string/app_name"
                  android:theme="@style/Theme.Billiards"
                  android:supportsRtl="true">
          
                  <activity
                      android:name=".MainActivity"
                      android:exported="true">
                      <intent-filter>
                          <action android:name="android.intent.action.MAIN" />
                          <category android:name="android.intent.category.LAUNCHER" />
                      </intent-filter>
                  </activity>
          
                  <activity
                      android:name=".AnalysisActivity"
                      android:exported="false" />
          
                  <activity
                      android:name=".SettingsActivity"
                      android:exported="false" />
          
              </application>
          
          </manifest>
          
          EOF_MANIFEST
          
          mkdir -p "app/src/main/res/values"
          cat > "app/src/main/res/values/strings.xml" << 'EOF_STRINGS'
          <resources>
              <string name="app_name">Billiards Trajectory Analyzer</string>
          </resources>
          
          EOF_STRINGS
          
          mkdir -p "app/src/main/res/values"
          cat > "app/src/main/res/values/colors.xml" << 'EOF_COLORS'
          <resources>
              <color name="felt_green">#0B6E4F</color>
              <color name="dark_bg">#121212</color>
              <color name="panel_bg">#1E1E1E</color>
              <color name="accent">#FFB300</color>
          </resources>
          
          EOF_COLORS
          
          mkdir -p "app/src/main/res/values"
          cat > "app/src/main/res/values/themes.xml" << 'EOF_THEMES'
          <resources>
              <style name="Theme.Billiards" parent="Theme.MaterialComponents.DayNight.NoActionBar">
                  <item name="colorPrimary">@color/felt_green</item>
                  <item name="colorPrimaryDark">@color/dark_bg</item>
                  <item name="colorAccent">@color/accent</item>
                  <item name="android:windowBackground">@color/dark_bg</item>
              </style>
          </resources>
          
          EOF_THEMES
          
          mkdir -p "app/src/main/res/layout"
          cat > "app/src/main/res/layout/activity_main.xml" << 'EOF_LAYOUT_MAIN'
          <?xml version="1.0" encoding="utf-8"?>
          <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
              android:layout_width="match_parent"
              android:layout_height="match_parent"
              android:orientation="vertical"
              android:gravity="center"
              android:background="@color/dark_bg"
              android:padding="32dp">
          
              <TextView
                  android:layout_width="wrap_content"
                  android:layout_height="wrap_content"
                  android:text="@string/app_name"
                  android:textColor="#FFFFFF"
                  android:textSize="24sp"
                  android:textStyle="bold"
                  android:gravity="center"
                  android:layout_marginBottom="8dp" />
          
              <TextView
                  android:layout_width="wrap_content"
                  android:layout_height="wrap_content"
                  android:text="Estimated shot prediction from a table photo"
                  android:textColor="#AAAAAA"
                  android:textSize="14sp"
                  android:gravity="center"
                  android:layout_marginBottom="48dp" />
          
              <Button
                  android:id="@+id/btnAnalyze"
                  android:layout_width="wrap_content"
                  android:layout_height="wrap_content"
                  android:text="ANALYZE IMAGE"
                  android:paddingLeft="32dp"
                  android:paddingRight="32dp"
                  android:paddingTop="16dp"
                  android:paddingBottom="16dp"
                  android:textSize="16sp" />
          
          </LinearLayout>
          
          EOF_LAYOUT_MAIN
          
          mkdir -p "app/src/main/res/layout"
          cat > "app/src/main/res/layout/activity_analysis.xml" << 'EOF_LAYOUT_ANALYSIS'
          <?xml version="1.0" encoding="utf-8"?>
          <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
              android:layout_width="match_parent"
              android:layout_height="match_parent"
              android:orientation="vertical"
              android:background="@color/dark_bg">
          
              <LinearLayout
                  android:layout_width="match_parent"
                  android:layout_height="wrap_content"
                  android:orientation="horizontal"
                  android:background="@color/panel_bg"
                  android:gravity="center_vertical">
          
                  <TextView
                      android:id="@+id/tvInstruction"
                      android:layout_width="0dp"
                      android:layout_height="wrap_content"
                      android:layout_weight="1"
                      android:padding="8dp"
                      android:textColor="#FFFFFF"
                      android:textSize="13sp"
                      android:text="Step 1: Adjust the table boundary corners, then continue." />
          
                  <Button
                      android:id="@+id/btnRedetect"
                      style="?android:attr/buttonStyleSmall"
                      android:layout_width="wrap_content"
                      android:layout_height="wrap_content"
                      android:text="DETECT"
                      android:textSize="10sp" />
          
                  <Button
                      android:id="@+id/btnSettings"
                      style="?android:attr/buttonStyleSmall"
                      android:layout_width="wrap_content"
                      android:layout_height="wrap_content"
                      android:text="⚙"
                      android:textSize="14sp" />
              </LinearLayout>
          
              <FrameLayout
                  android:layout_width="match_parent"
                  android:layout_height="0dp"
                  android:layout_weight="1">
          
                  <com.billiards.analyzer.TableView
                      android:id="@+id/tableView"
                      android:layout_width="match_parent"
                      android:layout_height="match_parent" />
              </FrameLayout>
          
              <LinearLayout
                  android:layout_width="match_parent"
                  android:layout_height="wrap_content"
                  android:orientation="vertical"
                  android:background="@color/panel_bg"
                  android:padding="4dp">
          
                  <LinearLayout
                      android:layout_width="match_parent"
                      android:layout_height="wrap_content"
                      android:orientation="horizontal">
          
                      <Button android:id="@+id/btnModeTable" style="?android:attr/buttonStyleSmall" android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:text="TABLE" android:textSize="10sp" />
                      <Button android:id="@+id/btnModePockets" style="?android:attr/buttonStyleSmall" android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:text="POCKETS" android:textSize="10sp" />
                      <Button android:id="@+id/btnModeBalls" style="?android:attr/buttonStyleSmall" android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:text="BALLS" android:textSize="10sp" />
                      <Button android:id="@+id/btnModeCue" style="?android:attr/buttonStyleSmall" android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:text="CUE" android:textSize="10sp" />
                      <Button android:id="@+id/btnModeTarget" style="?android:attr/buttonStyleSmall" android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:text="TARGET" android:textSize="10sp" />
                      <Button android:id="@+id/btnModePocketSelect" style="?android:attr/buttonStyleSmall" android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:text="POCKET" android:textSize="10sp" />
                  </LinearLayout>
          
                  <LinearLayout
                      android:layout_width="match_parent"
                      android:layout_height="wrap_content"
                      android:orientation="horizontal"
                      android:gravity="center_vertical">
          
                      <Button android:id="@+id/btnPowerLow" style="?android:attr/buttonStyleSmall" android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:text="LOW" android:textSize="10sp" />
                      <Button android:id="@+id/btnPowerMed" style="?android:attr/buttonStyleSmall" android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:text="MEDIUM" android:textSize="10sp" />
                      <Button android:id="@+id/btnPowerHigh" style="?android:attr/buttonStyleSmall" android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:text="HIGH" android:textSize="10sp" />
          
                      <ToggleButton
                          android:id="@+id/toggleBank"
                          android:layout_width="0dp"
                          android:layout_height="wrap_content"
                          android:layout_weight="1"
                          android:textOn="BANK:ON"
                          android:textOff="BANK:OFF"
                          android:textSize="9sp"
                          android:checked="false" />
                  </LinearLayout>
          
                  <LinearLayout
                      android:layout_width="match_parent"
                      android:layout_height="wrap_content"
                      android:orientation="horizontal"
                      android:gravity="center_vertical">
          
                      <TextView
                          android:id="@+id/tvPowerValue"
                          android:layout_width="wrap_content"
                          android:layout_height="wrap_content"
                          android:textColor="#FFFFFF"
                          android:textSize="11sp"
                          android:paddingStart="6dp"
                          android:paddingEnd="4dp"
                          android:text="Power: 65" />
          
                      <SeekBar
                          android:id="@+id/seekPower"
                          android:layout_width="0dp"
                          android:layout_height="wrap_content"
                          android:layout_weight="1"
                          android:max="100"
                          android:progress="65" />
                  </LinearLayout>
          
                  <LinearLayout
                      android:layout_width="match_parent"
                      android:layout_height="wrap_content"
                      android:orientation="horizontal"
                      android:layout_marginTop="4dp">
          
                      <Button
                          android:id="@+id/btnCalculate"
                          android:layout_width="0dp"
                          android:layout_height="wrap_content"
                          android:layout_weight="2"
                          android:text="CALCULATE"
                          android:textStyle="bold" />
          
                      <Button
                          android:id="@+id/btnRemoveBall"
                          android:layout_width="0dp"
                          android:layout_height="wrap_content"
                          android:layout_weight="1"
                          android:textSize="11sp"
                          android:text="REMOVE BALL" />
          
                      <Button
                          android:id="@+id/btnReset"
                          android:layout_width="0dp"
                          android:layout_height="wrap_content"
                          android:layout_weight="1"
                          android:text="RESET" />
                  </LinearLayout>
              </LinearLayout>
          
          </LinearLayout>
          
          EOF_LAYOUT_ANALYSIS
          
          mkdir -p "app/src/main/res/layout"
          cat > "app/src/main/res/layout/activity_settings.xml" << 'EOF_LAYOUT_SETTINGS'
          <?xml version="1.0" encoding="utf-8"?>
          <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
              android:layout_width="match_parent"
              android:layout_height="match_parent"
              android:orientation="vertical"
              android:background="@color/dark_bg"
              android:padding="20dp">
          
              <TextView
                  android:layout_width="wrap_content"
                  android:layout_height="wrap_content"
                  android:text="Settings"
                  android:textColor="#FFFFFF"
                  android:textSize="20sp"
                  android:textStyle="bold"
                  android:layout_marginBottom="24dp" />
          
              <LinearLayout
                  android:layout_width="match_parent"
                  android:layout_height="wrap_content"
                  android:orientation="horizontal"
                  android:gravity="center_vertical"
                  android:layout_marginBottom="16dp">
          
                  <TextView
                      android:layout_width="0dp"
                      android:layout_height="wrap_content"
                      android:layout_weight="1"
                      android:textColor="#FFFFFF"
                      android:textSize="15sp"
                      android:text="Auto-detect table/balls/pockets on import" />
          
                  <Switch
                      android:id="@+id/switchAutoDetect"
                      android:layout_width="wrap_content"
                      android:layout_height="wrap_content" />
              </LinearLayout>
          
              <LinearLayout
                  android:layout_width="match_parent"
                  android:layout_height="wrap_content"
                  android:orientation="horizontal"
                  android:gravity="center_vertical"
                  android:layout_marginBottom="16dp">
          
                  <TextView
                      android:layout_width="0dp"
                      android:layout_height="wrap_content"
                      android:layout_weight="1"
                      android:textColor="#FFFFFF"
                      android:textSize="15sp"
                      android:text="Show detection guide overlays" />
          
                  <Switch
                      android:id="@+id/switchGuides"
                      android:layout_width="wrap_content"
                      android:layout_height="wrap_content" />
              </LinearLayout>
          
              <TextView
                  android:layout_width="wrap_content"
                  android:layout_height="wrap_content"
                  android:textColor="#FFFFFF"
                  android:textSize="15sp"
                  android:text="Default power" />
          
              <SeekBar
                  android:id="@+id/seekDefaultPower"
                  android:layout_width="match_parent"
                  android:layout_height="wrap_content"
                  android:max="100" />
          
              <TextView
                  android:id="@+id/tvDefaultPowerValue"
                  android:layout_width="wrap_content"
                  android:layout_height="wrap_content"
                  android:textColor="#AAAAAA"
                  android:textSize="12sp"
           
