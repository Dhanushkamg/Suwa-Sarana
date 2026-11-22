#!/usr/bin/env bash
# =====================================================================================================
# Suwa Sarana — Automated EC2 Instance Setup Script (Ubuntu 24.04 LTS / 22.04 LTS)
# Configures Swap, Docker, Firewall, and prepares environment for AWS Free Tier
# =====================================================================================================

set -e

echo "=== [1/5] Updating system packages ==="
sudo apt-get update -y
sudo apt-get upgrade -y
sudo apt-get install -y curl wget git ufw apt-transport-https ca-certificates gnupg lsb-release

echo "=== [2/5] Creating 2GB Swapfile (Vital for 1GB RAM Free Tier Instances) ==="
if [ ! -f /swapfile ]; then
    echo "Creating swap file..."
    sudo fallocate -l 2G /swapfile || sudo dd if=/dev/zero of=/swapfile bs=1M count=2048
    sudo chmod 600 /swapfile
    sudo mkswap /swapfile
    sudo swapon /swapfile
    echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
    # Optimize swappiness for low memory instance
    sudo sysctl vm.swappiness=20
    echo 'vm.swappiness=20' | sudo tee -a /etc/sysctl.conf
    echo "Swap created successfully:"
    free -h
else
    echo "Swapfile already exists."
fi

echo "=== [3/5] Installing Docker Engine & Docker Compose Plugin ==="
if ! command -v docker &> /dev/null; then
    sudo install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg --yes
    sudo chmod a+r /etc/apt/keyrings/docker.gpg

    echo \
      "deb [arch="$(dpkg --print-architecture)" signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
      "$(. /etc/os-release && echo "$VERSION_CODENAME")" stable" | \
      sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

    sudo apt-get update -y
    sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

    # Enable and start Docker service
    sudo systemctl enable docker
    sudo systemctl start docker

    # Add current user to docker group
    sudo usermod -aG docker $USER
    echo "Docker installed successfully."
else
    echo "Docker already installed."
fi

echo "=== [4/5] Configuring UFW Firewall ==="
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 22/tcp comment 'SSH'
sudo ufw allow 80/tcp comment 'HTTP'
sudo ufw allow 443/tcp comment 'HTTPS'
sudo ufw --force enable
sudo ufw status verbose

echo "=== [5/5] Generating Secure .env.production Template ==="
if [ ! -f .env.production ]; then
    RAND_JWT=$(openssl rand -base64 64 | tr -d '\n')
    RAND_DB_PASS=$(openssl rand -hex 16)
    
    cat <<EOF > .env.production
# Database Configuration
POSTGRES_DB=suwa_sarana_prod
DB_USER=suwa_admin
DB_PASSWORD=${RAND_DB_PASS}
DB_URL=jdbc:postgresql://db:5432/suwa_sarana_prod

# Security & Authentication
JWT_SECRET=${RAND_JWT}

# Networking & URLs (Replace with your EC2 Public IP or Custom Domain)
# Example: http://54.210.12.34 or https://suwasarana.lk
PUBLIC_HOST=http://localhost
CORS_ALLOWED_ORIGIN=http://localhost
FRONTEND_URL=http://localhost
PUBLIC_BASE_URL=http://localhost

# Frontend Build Arguments
NEXT_PUBLIC_API_URL=/api
NEXT_PUBLIC_SITE_URL=http://localhost
NEXT_PUBLIC_GOOGLE_CLIENT_ID=

# Testing on HTTP before SSL setup (set false once HTTPS domain is attached)
ALLOW_HTTP=true
COOKIE_SECURE=false

# Optional Integrations
GROQ_API_KEY=
GOOGLE_CLIENT_ID=
SMS_PROVIDER_API_KEY=mock_key
EOF
    echo ".env.production generated with secure cryptographically random credentials!"
else
    echo ".env.production already exists."
fi

echo ""
echo "=========================================================================="
echo " AWS EC2 Free Tier Setup Complete!"
echo "=========================================================================="
echo "Next steps:"
echo "1. If you just added your user to the docker group, run: newgrp docker"
echo "2. Edit .env.production to set your EC2 Public IP or Domain: nano .env.production"
echo "3. Build and launch: docker compose -f docker-compose.prod.yml --env-file .env.production up -d --build"
echo "=========================================================================="
