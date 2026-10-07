"""Checks of the public download page (site/index.html). Run: python3 -m unittest discover -s tests/site -v

The page is the first thing a stranger sees, so these guard the things that must never silently break:
the download link, both languages, and that it loads nothing from other websites except GitHub.
"""
import re
import unittest
from html.parser import HTMLParser
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HTML = (ROOT / "site" / "index.html").read_text(encoding="utf-8")
APK_URL = "https://github.com/lestersinjacreater/Linda-test-app./releases/download/latest-build/linda-debug.apk"


class Collect(HTMLParser):
    def __init__(self):
        super().__init__()
        self.links, self.externals, self.langs = [], [], set()
        self.lang_counts = {"en": 0, "sw": 0}   # real content elements only (the <html> tag is excluded)

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if a.get("lang"):
            self.langs.add(a["lang"])
            if tag != "html" and a["lang"] in self.lang_counts:
                self.lang_counts[a["lang"]] += 1
        if tag == "a" and a.get("href"):
            self.links.append(a["href"])
        for key in ("src", "href"):
            v = a.get(key, "")
            if tag in ("script", "img", "link", "iframe") and v.startswith(("http://", "https://", "//")):
                self.externals.append(v)


page = Collect()
page.feed(HTML)


class DownloadPage(unittest.TestCase):
    def test_the_main_button_links_to_the_stable_apk_address(self):
        self.assertIn(f'id="apk" href="{APK_URL}"', HTML)

    def test_the_apk_address_matches_what_the_workflow_publishes(self):
        workflow = (ROOT / ".github" / "workflows" / "build-apk.yml").read_text(encoding="utf-8")
        self.assertIn("latest-build", workflow)
        self.assertIn("linda-debug.apk", workflow)

    def test_both_languages_are_present_for_every_visible_block(self):
        self.assertTrue({"en", "sw"} <= page.langs)
        self.assertGreater(page.lang_counts["en"], 20)
        self.assertEqual(page.lang_counts["en"], page.lang_counts["sw"], "every English block needs its Kiswahili twin")

    def test_it_works_without_javascript_so_the_link_is_plain_html(self):
        self.assertIn('<a class="btn" id="apk" href="https://', HTML)   # a real link, not a button that needs script
        self.assertNotIn("onclick=", HTML)

    def test_it_loads_no_scripts_images_or_styles_from_other_sites(self):
        self.assertEqual(page.externals, [])
        self.assertNotRegex(HTML, r"<script[^>]+src=")

    def test_the_only_network_call_is_to_the_github_api(self):
        calls = re.findall(r"https?://[^\"'\s)]+", re.sub(r'<a [^>]*href="[^"]*"', "", HTML))
        self.assertTrue(all(c.startswith(("https://api.github.com/", "https://github.com/", "http://www.w3.org/")) for c in calls), calls)

    def test_it_states_that_message_text_is_never_sent(self):
        self.assertIn("never sends the text of your messages", HTML)
        self.assertIn("haitumi maandishi ya jumbe zako", HTML)

    def test_every_permission_the_app_declares_is_explained_on_the_page(self):
        manifest = (ROOT / "android" / "app" / "src" / "main" / "AndroidManifest.xml").read_text(encoding="utf-8")
        declared = set(re.findall(r'uses-permission android:name="android.permission.(\w+)"', manifest))
        explained = {
            "RECEIVE_SMS": "Receive text messages", "READ_SMS": "Read text messages", "READ_CONTACTS": "Contacts",
            "POST_NOTIFICATIONS": "Notifications", "SEND_SMS": "Send text messages", "INTERNET": "Internet",
        }
        self.assertEqual(declared, set(explained), "a permission was added or removed: update the table on the page")
        for text in explained.values():
            self.assertIn(text, HTML)

    def test_it_tells_people_the_app_is_a_prototype_and_not_on_the_play_store(self):
        self.assertIn("prototype made for a hackathon", HTML)
        self.assertIn("not in the Play Store", HTML)

    def test_the_minimum_android_version_matches_the_app(self):
        gradle = (ROOT / "android" / "app" / "build.gradle.kts").read_text(encoding="utf-8")
        min_sdk = int(re.search(r"minSdk = (\d+)", gradle).group(1))
        self.assertEqual(min_sdk, 26)  # Android 8.0
        self.assertIn("Android 8.0", HTML)

    def test_tap_targets_are_large_for_older_or_new_smartphone_users(self):
        self.assertRegex(HTML, r"min-height:\s*64px")
        self.assertRegex(HTML, r"font:\s*18px")


if __name__ == "__main__":
    unittest.main()
