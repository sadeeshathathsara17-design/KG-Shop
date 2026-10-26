KG TRADERS - GitHub eken APK ekak hadana widiha
================================================

A) Phone eken (eka lesi wena widiha)
------------------------------------
1. github.com walata login wenna  ->  "+" -> New repository
   Name: kg-traders-apk   (Private karanna)  -> Create repository

2. Repository eke  "uploading an existing file"  (Add file -> Upload files) click karanna.
   Me zip eka extract karala, athule thiyena files ALL select karala upload karanna:
     index.html, MainActivity.java, AndroidManifest.xml, app-build.gradle, build.gradle,
     settings.gradle, gradle.properties, strings.xml, styles.xml, file_paths.xml,
     icons.zip, kgtraders.keystore
   (nathnam zip eka extract nokara ekama zip file eka upload karannath puluwan)
   -> Commit changes

3. Add file -> Create new file
   File name ekata mehema type karanna (slash walin folder hadenawa):
       .github/workflows/build-apk.yml
   "build-apk.yml" file eke thiyena okkoma text eka copy karala paste karanna -> Commit changes

4. "Actions" tab ekata yanna -> "Build KG Traders APK" run wenawa (minute 5-8k).
   Kola tick (green) unama:
     Repository eke  dakunu paththe  "Releases"  -> "KG Traders APK #1"  -> KG-Traders-v3.2-release.apk
   Eka download karala install karanna (Settings eke "Install unknown apps" allow karanna).

B) Aluth wenas kamak ekata (UI wenas kala nam)
-----------------------------------------------
index.html eka edit / replace karala commit karanna -> APK eka auto ayeth hadenawa.

Notes
-----
* App id: lk.kgtraders.app    version: 3.2 (app-build.gradle eke versionCode / versionName wenas karanna)
* kgtraders.keystore ekama sign karanna use karanawa - eka nathi nokara thiyaganna.
  (Eka thiyenakam aluth APK eka palaweni APK ekata uda install wenawa; data keep wenawa.)
* Repository eka PRIVATE karanna (keystore eke password eka file eke thiyenawa).
* WhatsApp bill share, Print / Save as PDF, Back button - APK eke wada karanawa.
