# Android Studio ছাড়াই IslamicAssistant APK তৈরি

## কী লাগবে
- একটি GitHub account
- এই project-টি নিজের **Private** GitHub repository-তে আপলোড করা
- একবার GitHub repository-তে `RELEASE_KEYSTORE_PASSWORD` নামে Actions secret সেট করা

## ধাপ ১ — GitHub repository
1. https://github.com/new খুলুন।
2. Repository name দিন `IslamicAssistant` এবং **Private** নির্বাচন করুন।
3. এই ZIP extract করুন। `IslamicAIStarter` ফোল্ডারের ভেতরের সব ফাইল repository-তে upload করুন (ফোল্ডারসহ আরেকটি nested repository তৈরি করবেন না)।
4. GitHub-এ `Add file` → `Upload files` দিয়ে ফাইলগুলো upload/commit করুন। `.github/workflows/android-apk.yml` ফাইলটি অবশ্যই upload হয়েছে নিশ্চিত করুন।

## ধাপ ২ — Release signing password secret
1. Repository-তে `Settings` → `Secrets and variables` → `Actions` খুলুন।
2. `New repository secret` চাপুন।
3. Name: `RELEASE_KEYSTORE_PASSWORD`
4. Value: নিজে বানানো শক্তিশালী পাসওয়ার্ড দিন এবং নিরাপদে সংরক্ষণ করুন। পাসওয়ার্ড কাউকে/চ্যাটে পাঠাবেন না।

## ধাপ ৩ — APK build
1. `Actions` ট্যাবে যান।
2. `Build signed Android APK` workflow নির্বাচন করুন।
3. `Run workflow` চাপুন (বা main branch-এ commit করলেই workflow চলবে)।
4. সবুজ টিক এলে workflow run খুলুন।
5. নিচে `Artifacts` থেকে `IslamicAssistant-release` ZIP ডাউনলোড করুন।
6. ZIP extract করলে `IslamicAssistant-release.apk` এবং `release-keystore.jks` পাবেন।

## খুব গুরুত্বপূর্ণ
- APK-টি Release build এবং ওই run-এর password secret দিয়ে sign করা।
- `release-keystore.jks` ও পাসওয়ার্ড নিরাপদে আলাদা ব্যাকআপে রাখুন। ভবিষ্যতে আপডেট APK একই key দিয়ে sign করতে হবে; key হারালে পুরোনো ইনস্টলের ওপর update নাও বসতে পারে।
- এই workflow-তে তৈরি APK এখনো আপনার LDPlayer/ফোনে পরীক্ষা করা হয়নি। প্রথমবার ইনস্টল করে যাচাই করুন।
- GitHub Actions-এর cloud build-এ প্রজেক্টের source code ও audio assets GitHub repository-তে আপলোড হবে. Private repository ব্যবহার করুন.
- Workflow-টি Gradle 8.9 + JDK 17 ব্যবহার করে, কারণ প্রজেক্টে Android Gradle Plugin 8.7.3 সেট করা আছে।
