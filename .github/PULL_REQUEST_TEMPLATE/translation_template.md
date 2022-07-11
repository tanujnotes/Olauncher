---
name: Add Translation
about: Issue for a translation you have made and want to merge
title: ''
labels: ''
assignees: ''
---

<!--
Thank you for your help in making Olauncher CF better!

Guide to a good bug-report:
• Please search existing bug/crash reports reports to avoid creating duplicates.
• Give your bug report a good name (no generics like "Error" or "Bug"), so others can easily identify the topic of your issue.
• Describe the bug in a short but concise way.
• If you have a screenshot or screen recording of the bug, link them at the end of this issue.
• Also make sure to fill out the environment information. This info is valuable when trying to fix your described bug.
-->

#### Translation checklist

Here is a list of things that can be translated.
Not all of them are strictly needed.

- [ ] I have translated the strings.xml
- [ ] I have translated the short_description.md
- [ ] I have translated the full_description.md
- [ ] I have added the langage to the language list in the README
- [ ] I have added the langage to the language list in the full_description.md

#### Integration checklist

If you add a new translation there are some things that need to be added to the code.
If you know how, here is a checklist of things that need to be done.

- [ ] Add the language to the `Language` enum in `app/src/main/java/app/olaunchercf/data/Constants.kt` (Please use the english name here)
- [ ] Add the **native name** of the language to the `string()` method of the `Language` enum. (For example for `German` add `Deutsch`, for `Persian` add `فارسی`)
- [ ] Add the country code to the `value()` method of the `Language` enum.
