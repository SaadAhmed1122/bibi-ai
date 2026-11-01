# Bibi AI closed test: getting the model

For testers and Play's reviewers. The model isn't inside the app (it's 585 MB), so it comes from a Google Drive link. Decision 2026-11-01, open item 6.

**Drive link:** [OPEN: the owner pastes the folder link after uploading; sharing "Anyone with the link: Viewer".]

## Owner: what goes in the Drive folder
Folder name `Bibi model pack`:
1. `gemma3-1b-it-int4.litertlm` (needed). It must be byte-for-byte the file the app expects (size 584,417,280 bytes). Upload the same file the vivo imported.
2. `embeddinggemma-2-text-270m.litertlm` (optional, 164,626,432 bytes).
3. `bibi-voice/` (optional): one folder holding `parakeet-tdt-0.6b-v2-int8/` and `piper-en_US-amy-low/` with their files unchanged.
4. `NOTICE.txt` and `README.txt` from [docs/model-pack/](model-pack/).
5. `Gemma-Terms-of-Use.pdf`: open ai.google.dev/gemma/terms and save it as PDF. The Gemma Terms require recipients to get a copy, the notice file, and notice of the section 3.2 use restrictions [VERIFY: owner reads section 3 before sharing].

## Testers: install in 5 steps
1. Install Bibi AI from the Play test link in your invite.
2. On the phone, open the Drive link and download `gemma3-1b-it-int4.litertlm`. Drive says it can't scan large files for viruses: tap **Download anyway**. It lands in Downloads. You need about 1.2 GB free (the download plus Bibi's own copy); delete the download after step 4.
3. Open Bibi. On "My AI model", tap **Choose model file** and pick the file from Downloads.
4. Wait for "The model is ready" (a minute or two: Bibi copies and checks the file). If it says the file is damaged or the wrong size, download it again.
5. Optional, from the **Bibi's model** screen (Home > Bibi's brain):
   - **Phone actions:** download `embeddinggemma-2-text-270m.litertlm`, then Choose action model.
   - **Voice:** download the `bibi-voice` folder. Drive gives you a zip: open it in Files by Google and tap **Extract**. Then tap Choose voice folder and pick the extracted `bibi-voice` folder, the one that holds both voice folders.

Everything stays on the phone: the app has no internet permission, and the files are only read from where you saved them.

## Play Console: App access text
Paste into App content > App access > "All or some functionality is restricted", with these instructions:

> No login. The AI model (585 MB) is downloaded separately: open <Drive link>, download gemma3-1b-it-int4.litertlm, then in the app tap "Choose model file" on the first-run screen (or Home > Bibi's model) and pick it. Wait for "The model is ready". Optional voice: download the bibi-voice folder, extract the zip, then on Bibi's model choose that folder. To try the floating character: Home > Floating Bibi, allow "Display over other apps", go to the home screen, tap Bibi and speak.
