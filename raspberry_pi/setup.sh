#!/bin/bash

# 1. Copy the service file to the system directory
sudo cp freshedge.service /etc/systemd/system/freshedge.service

# 2. Set correct permissions
sudo chmod 644 /etc/systemd/system/freshedge.service

# 3. Reload systemd to recognize the new file
sudo systemctl daemon-reload

# 4. Enable it to run on every boot
sudo systemctl enable freshedge

# 5. Start it immediately
sudo systemctl start freshedge

# 6. Show status
sudo systemctl status freshedge