FROM mcr.microsoft.com/devcontainers/java:3-17-trixie

ARG USERNAME=vscode

# Kiro CLI用ディレクトリの作成と権限設定
RUN mkdir -p /home/${USERNAME}/.kiro /home/${USERNAME}/.local/share/kiro-cli && \
  chown -R ${USERNAME}:${USERNAME} /home/${USERNAME}/.kiro /home/${USERNAME}/.local

# non-root user
USER ${USERNAME}

# Kiro CLIのインストール先をPATHに追加
ENV PATH=$PATH:/home/${USERNAME}/.local/bin

# Kiro CLIのインストール
RUN curl -fsSL https://cli.kiro.dev/install | bash
