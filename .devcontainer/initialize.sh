#!/bin/bash

# -----------------------------------------------------------------------------
# gitignore
# -----------------------------------------------------------------------------
# VPN に接続していないとき，devcontainer 内から docker run しても正しい応答がない
# 場合があるため，WSL 上で実行できるように initializeCommand で実行する

# cf. https://github.com/github/gitignore
echo "# -----------------------------------------------------------------------------" > .gitignore
echo "# java" >> .gitignore
echo "# -----------------------------------------------------------------------------" >> .gitignore
docker run --rm simonwhitaker/gibo dump Java >> .gitignore

echo "# -----------------------------------------------------------------------------" >> .gitignore
echo "# gradle" >> .gitignore
echo "# -----------------------------------------------------------------------------" >> .gitignore
docker run --rm simonwhitaker/gibo dump Gradle >> .gitignore

echo "# -----------------------------------------------------------------------------" >> .gitignore
echo "# node" >> .gitignore
echo "# -----------------------------------------------------------------------------" >> .gitignore
docker run --rm simonwhitaker/gibo dump Node >> .gitignore

echo "# -----------------------------------------------------------------------------" >> .gitignore
echo "# vscode" >> .gitignore
echo "# -----------------------------------------------------------------------------" >> .gitignore
docker run --rm simonwhitaker/gibo dump VisualStudioCode >> .gitignore
echo "!.vscode/*.sh" >> .gitignore

echo "*.tar.gz" >> .gitignore

# .env は git 管理対象とする
sed -i -e 's/^\.env$/#\.env/g' .gitignore
