from typing import List
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from database import get_db
from models import Task, User
from schemas import TaskCreate, TaskResponse, TaskUpdate
from security import get_current_user

router = APIRouter(prefix="/tasks", tags=["Task Management"])


@router.post("", response_model=TaskResponse, status_code=status.HTTP_201_CREATED)
def create_task(
    task_in: TaskCreate,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    """
    Creates and persists a new automation task definition.
    Requires authentication.
    """
    task = Task(
        task_name=task_in.task_name,
        action_type=task_in.action_type,
        search_query=task_in.search_query,
    )
    task.action_params = task_in.action_params
    db.add(task)
    db.commit()
    db.refresh(task)
    return task


@router.get("", response_model=List[TaskResponse])
def fetch_tasks(db: Session = Depends(get_db)):
    """
    Fetches all available tasks.
    """
    return db.query(Task).all()


@router.get("/{task_id}", response_model=TaskResponse)
def fetch_task(task_id: int, db: Session = Depends(get_db)):
    """
    Fetches a specific task by its integer ID.
    Raises 404 if not found.
    """
    task = db.query(Task).filter(Task.id == task_id).first()
    if not task:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Task with ID {task_id} not found"
        )
    return task


@router.put("/{task_id}", response_model=TaskResponse)
def update_task(
    task_id: int,
    task_in: TaskUpdate,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    """
    Updates an existing task record.
    Requires authentication.
    """
    task = db.query(Task).filter(Task.id == task_id).first()
    if not task:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Task with ID {task_id} not found"
        )

    if task_in.task_name is not None:
        task.task_name = task_in.task_name
    if task_in.action_type is not None:
        task.action_type = task_in.action_type
    if task_in.search_query is not None:
        task.search_query = task_in.search_query
    if task_in.action_params is not None:
        task.action_params = task_in.action_params

    db.commit()
    db.refresh(task)
    return task
