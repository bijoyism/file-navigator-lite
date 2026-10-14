# Build the APK from an Android phone

This project uses GitHub Actions to build the APK, so you do not need to install Android Studio on your phone. GitHub must receive the **extracted project files**; uploading only the source ZIP as one file will not run the workflow.

## Method: Termux + GitHub CLI

1. Create a new empty repository in your GitHub account named `file-navigator-lite` (skip adding a README/license because this project already contains them).
2. Install Termux from a trusted official distribution, open it, and run:

   ```sh
   pkg update
   pkg install git unzip
   termux-setup-storage
   ```

3. Extract the downloaded `file-navigator-lite-source.zip` using your Android file manager. In Termux, move into the extracted project's root directory. For example, if it is under Downloads:

   ```sh
   cd ~/storage/downloads/file-navigator-lite
   ```

   The folder must contain `settings.gradle.kts` and `.github/workflows/build-apk.yml`. If it does not, you are one directory too high or too low.

4. Configure Git and push to your new repository:

   ```sh
   git init -b main
   git add .
   git commit -m "Add File Navigator Lite source"
   git remote add origin https://github.com/YOUR_USERNAME/file-navigator-lite.git
   git push -u origin main
   ```

   Replace `YOUR_USERNAME`. GitHub does not accept an account password for Git over HTTPS; authenticate using a personal access token when Git prompts, or configure an approved GitHub CLI/SSH method. Keep tokens private and never paste them into this project or a public issue.

5. In your browser, open the repository, select **Actions**, and open **Build Android APK**. Enable Actions if GitHub prompts, then choose **Run workflow**.
6. Open the run. Wait until it finishes and shows success. Download the `file-navigator-lite-debug-apk` artifact and extract it. The APK inside is named `app-debug.apk`.

## If the workflow fails

Open the failed run, expand the first failed step, and read its error. Download `file-navigator-lite-build-reports` when that artifact is available. A red run must not be treated as a working APK; fix the reported issue and push the changes to rerun CI.

## Install on Redmi 9i

Open the downloaded `app-debug.apk` on the phone. Android may ask you to allow installs from the app used to open the APK. After installation, launch File Navigator Lite and choose a disposable test folder. See the main README for a safe feature test checklist.
