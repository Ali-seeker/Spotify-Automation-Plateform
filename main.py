import os
import sys
from dotenv import load_dotenv

# Load environment variables from .env file
load_dotenv()

# Verify all required environment variables are present
REQUIRED_ENV_VARS = [
    "JWT_SECRET",
    "JWT_ALGORITHM",
    "ACCESS_TOKEN_EXPIRE_MINUTES",
    "DATABASE_URL",
    "DEVICE_SHARED_SECRET",
]

missing_vars = [var for var in REQUIRED_ENV_VARS if not os.getenv(var)]
if missing_vars:
    print(f"CRITICAL ERROR: Missing required environment variables: {', '.join(missing_vars)}")
    sys.exit(1)

from fastapi import FastAPI, Depends, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy.orm import Session
from database import engine, Base, SessionLocal, get_db
import models
import auth_router
from security import get_password_hash

# Automatically create all SQLite tables on startup
Base.metadata.create_all(bind=engine)

# Seed default admin user if database is empty
def seed_default_user():
    db = SessionLocal()
    try:
        user = db.query(models.User).filter(models.User.username == "admin").first()
        if not user:
            default_user = models.User(
                username="admin",
                hashed_password=get_password_hash("admin123"),
                is_active=True
            )
            db.add(default_user)
            db.commit()
            print("INFO: Default admin user seeded successfully (admin / admin123).")
    finally:
        db.close()

seed_default_user()

app = FastAPI(
    title="Spotify Automation Platform API",
    description="Backend API for the Spotify Automation Platform",
    version="1.0.0",
)

# Configure CORS for frontend development server
origins = [
    "http://localhost:5173",
    "http://127.0.0.1:5173",
    "http://localhost:3000",
    "http://127.0.0.1:3000",
]

app.add_middleware(
    CORSMiddleware,
    allow_origins=origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Include Authentication Router
app.include_router(auth_router.router)


@app.get("/")
def read_root():
    """
    Root endpoint.
    """
    return {
        "status": "online",
        "message": "Spotify Automation API is running cleanly",
        "docs_url": "/docs",
    }

@app.get("/health")
def health_check(db: Session = Depends(get_db)):
    """
    Health check endpoint to verify both API status and DB connectivity.
    """
    try:
        from sqlalchemy import text
        db.execute(text("SELECT 1"))
        return {
            "status": "healthy",
            "database": "connected",
            "environment": {
                "JWT_ALGORITHM": os.getenv("JWT_ALGORITHM"),
                "ACCESS_TOKEN_EXPIRE_MINUTES": os.getenv("ACCESS_TOKEN_EXPIRE_MINUTES"),
            }
        }
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"Database connection failed: {str(e)}"
        )
