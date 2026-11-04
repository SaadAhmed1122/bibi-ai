# Bibi AI closed test: invite and recruiting plan

SPEC-feedback.md (Task 4, #129). The owner sends the invite; the install steps are in [tester-guide.md](tester-guide.md).

## Who can test
- An Android phone with **Android 14 or newer** (the app's minimum).
- **4 GB of RAM or more.** 4 GB phones are the floor and are untested so far (open item 10) [VERIFY on a 4 GB phone]; the app was tested on a 6 GB phone.
- **About 1.2 GB free** for the chat model (download plus Bibi's copy), and about 0.9 GB more for the optional voice and phone-action files.
- A Google account, so they can join the Play test.

## Recruiting plan
- **Target: 14 testers**, so that at least 12 stay opted in. Play requires 12 testers opted in for 14 days in a row before a new personal account can apply for production [VERIFY current Play Console rule on the day, open item 3].
- **Sources, in order:**
  1. People the owner knows who have a suitable Android phone (friends, family, colleagues).
  2. A LinkedIn call for testers (the draft is below and in docs/linkedin/posts.md).
  3. Android developer communities the owner already belongs to, if 1 and 2 give fewer than 14.
- **Joining:** the owner adds each tester's Google account email to the closed-test tester list (or a Google Group) in Play Console, then sends them the invite below with the opt-in link.
- **Tracking:** the owner keeps a private list of testers outside the repository (names and emails never go into git). In reports and issues, testers are T1, T2, and so on.
- **Count check:** on day 3 and day 10, the owner checks the opted-in count in Play Console. If it is under 12, they invite the spares.

## The invite (send by email or chat)

> Hi! Thanks for helping test **Bibi AI**, a small AI companion that runs completely on your phone: no internet, no account, nothing leaves your phone.
>
> **1. Join the test:** open <Play opt-in link> on your Android phone and tap "Become a tester", then install Bibi AI from Play.
> **2. Get the AI model:** follow the 5 steps here: <link to the tester guide>. It's a 585 MB download from Google Drive.
> **3. Keep it installed for 14 days.** Play needs testers to stay in for two weeks, so please don't leave the test early, even if you only use Bibi a little.
>
> **What to try**
> - **Week 1:** chat with Bibi in the app. Ask Bibi to remember something ("remember my sister is called Sara"), then ask about it later. Try a phone action: "turn on the flashlight", "set a timer for 5 minutes".
> - **Week 2:** turn on Floating Bibi (Home), tap it over another app and talk to it. If you downloaded the voice files, try "read answers aloud" too.
>
> **Two small favors**
> - On **day 7** and **day 14**: Settings → Share usage numbers → Share, and send it to me. It's only numbers and dates (how often you used Bibi and whether it crashed); you can read all of it before sending.
> - Anything odd, slow or confusing: Settings → Send feedback. Short is fine.
>
> Thank you! 🙏

The two `<…>` slots are filled by the owner when sending: the opt-in link exists once the closed-test track is set up, and the tester-guide link once it is hosted (or pasted as text).

## Reminders
- **Day 7:** "Hi! It's day 7. Could you send your usage numbers (Settings → Share usage numbers)? And how's Bibi so far?"
- **Day 14:** "Last day of the test! Please send your usage numbers one more time. You can keep Bibi installed. Thanks a lot for helping."

## LinkedIn call for testers (draft)

> I'm looking for **12–14 Android testers** for two weeks. 🙋
>
> I've been building **Bibi AI**, an AI companion that runs completely on the phone: a local Gemma model, voice, memory and a few phone actions, with **no internet permission at all**. It's now ready for a closed test on Google Play.
>
> You'd need: an Android 14+ phone with 4 GB RAM or more, about 1.5 GB free space, and 14 days of staying in the test (using it a few times is enough).
>
> Comment or DM me and I'll send the invite. I'll share the results (crash-free rate, what people used) here afterwards.
>
> #OnDeviceAI #Android #BuildInPublic #BetaTesting
