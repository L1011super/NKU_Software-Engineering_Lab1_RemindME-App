package com.remindme.model;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class Task {
    private static final DateTimeFormatter ID_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private String id;
    private String title;
    private String description;
    private LocalDateTime dueTime;
    private LocalTime remindTime;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private boolean completed;
    private Priority priority;
    private List<SubTask> subTasks;

    public Task() {
        this.id = generateId();
        this.remindTime = LocalTime.of(9, 0);
        this.createdAt = LocalDateTime.now();
        this.completedAt = null;
        this.completed = false;
        this.priority = Priority.MEDIUM;
        this.subTasks = new ArrayList<>();
    }

    public Task(String title, String description, LocalDateTime dueTime, LocalTime remindTime, Priority priority) {
        this.id = generateId();
        this.title = title;
        this.description = description;
        this.dueTime = dueTime;
        this.remindTime = remindTime != null ? remindTime : LocalTime.of(9, 0);
        this.createdAt = LocalDateTime.now();
        this.completedAt = null;
        this.completed = false;
        this.priority = priority;
        this.subTasks = new ArrayList<>();
    }

    public Task(String title, String description, LocalDateTime dueTime, Priority priority) {
        this(title, description, dueTime, LocalTime.of(9, 0), priority);
    }

    public void setId(String id) {
        this.id = (id == null || id.isBlank()) ? generateId() : id;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getDueTime() {
        return dueTime;
    }

    public void setDueTime(LocalDateTime dueTime) {
        this.dueTime = dueTime;
    }

    public LocalTime getRemindTime() {
        return remindTime;
    }

    public void setRemindTime(LocalTime remindTime) {
        this.remindTime = remindTime != null ? remindTime : LocalTime.of(9, 0);
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        if (this.completed == completed) {
            return;
        }
        this.completed = completed;
        this.completedAt = completed ? LocalDateTime.now() : null;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public List<SubTask> getSubTasks() {
        if (subTasks == null) {
            subTasks = new ArrayList<>();
        }
        return subTasks;
    }

    public void setSubTasks(List<SubTask> subTasks) {
        this.subTasks = subTasks == null ? new ArrayList<>() : new ArrayList<>(subTasks);
        syncCompletionWithSubTasks();
    }

    public boolean hasSubTasks() {
        return !getSubTasks().isEmpty();
    }

    public boolean areAllSubTasksCompleted() {
        return hasSubTasks() && getSubTasks().stream().allMatch(SubTask::isCompleted);
    }

    public void syncCompletionWithSubTasks() {
        if (!hasSubTasks()) {
            return;
        }
        setCompleted(areAllSubTasksCompleted());
    }

    @Override
    public String toString() {
        return title;
    }

    private static String generateId() {
        return LocalDateTime.now().format(ID_FORMATTER);
    }

    public static class SubTask {
        private String title;
        private boolean completed;

        public SubTask() {
        }

        public SubTask(String title) {
            this.title = title;
            this.completed = false;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public boolean isCompleted() {
            return completed;
        }

        public void setCompleted(boolean completed) {
            this.completed = completed;
        }
    }
}
