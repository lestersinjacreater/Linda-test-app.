# The download page

A small web page where anyone can download Linda for Android and install it. It lives in `site/index.html` and is
hosted free by **GitHub Pages**. Nothing runs on a server, and nothing needs installing on your computer.

## What people see

- A big **Download Linda for Android** button, in English or Kiswahili (a button switches language).
- Install steps for a phone, the list of permissions and why, an honest "is it safe?" note (a hackathon prototype, not in the Play Store).
- On a computer or iPhone: a note to open the page on an Android phone, and a **QR code** to scan.
- When the newest build was made, its size, and (if GitHub provides it) the file's SHA-256 fingerprint.

The button always points at `https://github.com/lestersinjacreater/Linda-test-app./releases/download/latest-build/linda-debug.apk`.
That address never changes: every build replaces the file behind it. So the page itself never needs updating when the app changes.

## One-time setup (the repository owner, in the browser)

1. **Merge the work into the default branch** (a pull request from `claude/zealous-shannon-gz3o6g`). The page and the release only publish from the default branch.
2. **Settings > Pages**. Under **Build and deployment > Source**, choose **GitHub Actions**. (This is the one thing the workflow cannot switch on for you.)
3. Open the **Actions** tab. After the merge, two workflows run:
   - **Build APK** builds the app and publishes `linda-debug.apk` to the **Releases** page (as `latest-build`).
   - **download page** tests the page and publishes it.
4. When both are green, the page address is shown in **Settings > Pages** (it looks like `https://lestersinjacreater.github.io/Linda-test-app./`). Open it on your phone.

Rerun either one by hand any time: **Actions > (workflow name) > Run workflow**.

## Checking it works

- Open the page address on an Android phone, tap Download, install, open Linda.
- If the page loads but the button gives "not found", the APK has not been published yet: run **Build APK** by hand on the default branch.
- If **Settings > Pages** shows no address, step 2 was missed.
- The QR code is made by the workflow each time. If it is missing the page hides it and still works.

## Things to know

- **The repository name ends with a dot** (`Linda-test-app.`), which is unusual. The Pages address would end in `/Linda-test-app./`. If your phone or browser handles that address badly, rename the repository (Settings > General > Repository name) to `Linda-test-app` and update the address in `site/index.html` (the `REPO` value and the two links); the test in `tests/site` will tell you what to change.
- **The APK is a debug build**, signed with the project's fixed debug key, so later builds install over earlier ones. It is not for the Play Store.
- **What the page cannot do:** count downloads, or stop someone sharing the link. Anyone with the address can install it.
- **Updating the text:** edit `site/index.html`. Every English block has a Kiswahili twin, and a test fails if one is missing or if the permission table no longer matches the app.
