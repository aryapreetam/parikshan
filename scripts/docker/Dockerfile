FROM eclipse-temurin:21-jdk-noble

ENV DEBIAN_FRONTEND=noninteractive

# 1. System tools, Xvfb, Mesa GL, font suites, and dual-JDK 17 (for :build-logic toolchain)
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    wget \
    unzip \
    git \
    xvfb \
    xauth \
    libgbm1 \
    libatomic1 \
    libxtst6 \
    libxi6 \
    libxrender1 \
    libxrandr2 \
    libxcursor1 \
    libasound2t64 \
    openjdk-17-jdk-headless \
    mesa-utils \
    libgl1-mesa-dri \
    fonts-liberation \
    fonts-dejavu-core \
    fonts-noto-color-emoji \
    ca-certificates \
 && rm -rf /var/lib/apt/lists/*

# 2. Install Node.js LTS 20.x
RUN curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
 && apt-get install -y nodejs \
 && rm -rf /var/lib/apt/lists/*

# 3. Pre-install Playwright's full Chromium browser and system dependencies
#    Matches project playwright version (1.50.0 -> chromium-1208)
ENV PLAYWRIGHT_BROWSERS_PATH=/root/.cache/ms-playwright
RUN npx -y playwright@1.50.0 install --with-deps chromium

# 4. Install Android SDK (Command-line tools + Platform 35) for Android Unit Tests
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

RUN mkdir -p ${ANDROID_HOME}/cmdline-tools \
 && curl -fsSL -o /tmp/cmdline-tools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip \
 && unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools-extracted \
 && mv /tmp/cmdline-tools-extracted/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest \
 && rm -rf /tmp/cmdline-tools.zip /tmp/cmdline-tools-extracted \
 && yes | sdkmanager --licenses > /dev/null \
 && sdkmanager "platforms;android-35" "platform-tools" "build-tools;35.0.0" > /dev/null

# 5. Entrypoint for automated Xvfb lifecycle and clean signal propagation
COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

ENV DISPLAY=:99
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
