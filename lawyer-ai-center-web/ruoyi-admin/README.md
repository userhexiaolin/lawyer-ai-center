> **与律行 / YuLvXing：** 本目录保留上游项目说明以便溯源。当前衍生版本的安装、公司联系、许可与免责说明请以[仓库根 README](../README.md) / [English README](../README-en.md)为入口；以下上游演示与镜像不代表本项目发行版。

# RuoYi-AI Admin Panel

<div align="center">

<img src="https://github.com/ageerle/ruoyi-ai/raw/main/docs/image/logo.png" alt="RuoYi AI Logo" width="120" height="120">

### Enterprise-Grade AI Assistant Platform - Admin Panel

*The RuoYi-AI admin panel for system management, model configuration, knowledge-base management, and workflow orchestration.*

**[中文](README_ZH.md)** | **[Live Demo](https://admin.ruoyiai.chat/chat)** | **[Backend Service](https://github.com/ageerle/ruoyi-ai)** | **[User Frontend](https://github.com/ageerle/ruoyi-web)**

</div>

## Tech Stack

- **Framework**: Vue 3 + Vben Admin
- **UI Components**: element-plus-x
- **Build Tool**: Vite

## Docker Deployment

This admin panel supports two Docker deployment methods:

### Method 1: Start All Services with One Command (Recommended)

Use `docker-compose-all.yaml` to start all services at once, including the backend, admin panel, user frontend, and dependencies:

```bash
# Clone the backend repository
git clone https://github.com/ageerle/ruoyi-ai.git
cd ruoyi-ai

# Start all services using pre-built images
docker-compose -f docker-compose-all.yaml up -d

# Open the admin panel
# URL: http://localhost:25666
# Account: admin / admin123
```

### Method 2: Deploy Services Separately (Build from Source)

If you need to build from source, follow these steps:

#### Step 1: Deploy the Backend Service

```bash
# Enter the backend project directory
cd ruoyi-ai

# Start the backend service and build from source
docker-compose up -d --build

# Wait for the backend service to start
docker-compose logs -f backend
```

#### Step 2: Deploy the Admin Panel

```bash
# Enter the admin project directory
cd ruoyi-admin

# Build and start the admin panel
docker-compose up -d --build

# Open the admin panel
# URL: http://localhost:5666
```

#### Step 3: Deploy the User Frontend (Optional)

```bash
# Enter the user frontend project directory
cd ruoyi-web

# Build and start the user frontend
docker-compose up -d --build

# Open the user frontend
# URL: http://localhost:5137
```

### Service Ports

| Service | Port | Description |
|------|------|------|
| Admin panel | 5666 | Admin panel URL |
| User frontend | 5137 | User frontend URL |
| Backend service | 6039 | Backend API service |
| MySQL | 23306 | Database service |
| Redis | 6379 | Cache service |
| Weaviate | 28080 | Vector database |
| MinIO | 9000/9090 | Object storage |

## Local Development

```bash
# Install dependencies
pnpm install

# Start the development server
pnpm dev

# Build the production version
pnpm build
```

## FAQ

**Q: The admin panel cannot connect to the backend service.**

A: Make sure the backend service is running and verify that the `UPSTREAM_HOST` environment variable is configured correctly.

**Q: What is the difference between one-command startup and separate deployment?**

A: One-command startup uses pre-built images for faster deployment. Separate deployment builds from source and is suitable when custom changes are required.

## License

This project is licensed under the **MIT License**. See the [LICENSE](license) file for details.

---

<div align="center">

**[⭐ Star this project](https://github.com/ageerle/ruoyi-admin)** • **[Fork and contribute](https://github.com/ageerle/ruoyi-admin/fork)**

*Made with ❤️ and maintained by the RuoYi AI open-source community*

</div>
