# Permission to use the Kenyan scam SMS data (Salim Mwatsefu)

**Dataset:** `data/raw/scam_detection_labeled.csv` in https://github.com/Salimmwatsefu/NLP-Scam-Detection
(commit b38138c), by Salim Mwatsefu (IwazoLab). The repository has no licence file, so we asked the author.

**Granted to:** Lester Sinja, in writing, 21 Oct 2026. The author's words, verbatim:

> Hi, Lester
>
> I hereby grant you permission to access, use, download, copy, analyze, modify, and reproduce the data contained in my
> GitHub repository for your intended project or research purposes. You may also process the data, integrate it into
> your applications or analytical workflows, and share derived datasets or results, provided that any applicable
> third-party rights and legal requirements are respected.
>
> You are authorized to use the repository's contents to the extent that I have the rights to grant such permission.
> Where appropriate, please acknowledge the original repository as the source and retain relevant attribution or
> licensing notices. If you intend to redistribute the original data or use it commercially, please ensure that any
> applicable licenses and third-party restrictions permit such use.
>
> This permission is granted in good faith to facilitate your work, and you may contact me if you need any additional
> clarification or authorization.
>
> GitHub Repository: https://github.com/Salimmwatsefu/NLP-Scam-Detection.git
>
> Best regards for your project.

## How Linda keeps to it

- **Acknowledged** as the source in `DATASETS.md`, `ml/data/README.md` and `ml/reports/metrics.md` (the "kenya_sms" source).
- **The original data is not redistributed.** The raw file stays in the git-ignored `ml/data/raw/kenya_sms_mwatsefu.csv`
  on the machine that trains the model. The repo holds only our own derived file `ml/data/labels/kenya_sms_labels.csv`
  (a 16-character fingerprint of each message plus our label; no text), which the permission allows ("share derived
  datasets or results").
- **Not commercial:** used for a Safaricom intern hackathon prototype. If Linda ever went beyond that, the author's
  permission for commercial use, and any third-party rights in the messages, would need checking first.
