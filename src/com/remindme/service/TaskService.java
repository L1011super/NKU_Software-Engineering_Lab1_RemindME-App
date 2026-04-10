package com.remindme.service;

import com.remindme.model.Task;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import com.remindme.util.AppPaths;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class TaskService {
    private final ObservableList<Task> taskList = FXCollections.observableArrayList();

    private static final Path DATA_DIR = Paths.get("usrdata");
    private static final Path DATA_FILE = DATA_DIR.resolve("data.json");

    /**
     * Gson 对同一类型多次 registerTypeAdapter 时，后注册的会覆盖先注册的工厂，
     * 原先分别注册 Serializer/Deserializer 会导致 LocalDateTime/LocalTime 只有一种生效，写入 JSON 异常。
     */
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new TypeAdapter<LocalDateTime>() {
                @Override
                public void write(JsonWriter out, LocalDateTime value) throws IOException {
                    if (value == null) {
                        out.nullValue();
                    } else {
                        out.value(value.toString());
                    }
                }

                @Override
                public LocalDateTime read(JsonReader in) throws IOException {
                    if (in.peek() == JsonToken.NULL) {
                        in.nextNull();
                        return null;
                    }
                    return LocalDateTime.parse(in.nextString());
                }
            })
            .registerTypeAdapter(LocalTime.class, new TypeAdapter<LocalTime>() {
                @Override
                public void write(JsonWriter out, LocalTime value) throws IOException {
                    if (value == null) {
                        out.nullValue();
                    } else {
                        out.value(value.toString());
                    }
                }

                @Override
                public LocalTime read(JsonReader in) throws IOException {
                    if (in.peek() == JsonToken.NULL) {
                        in.nextNull();
                        return null;
                    }
                    return LocalTime.parse(in.nextString());
                }
            })
            .setPrettyPrinting()
            .create();
    public ObservableList<Task> getTaskList() {
        return taskList;
    }


    //初始化和相关列表处理
    public TaskService() {
        loadTasks();
    }

    private void saveTasks() {
        try {
            if (Files.notExists(DATA_DIR)) {
                Files.createDirectories(DATA_DIR);
            }

            try (Writer writer = Files.newBufferedWriter(DATA_FILE, StandardCharsets.UTF_8)) {
                gson.toJson(new ArrayList<>(taskList), writer);
            }
        } catch (IOException | JsonIOException | JsonSyntaxException e) {
            e.printStackTrace();
        }
    }
    private void loadTasks() {
        try {
            if (Files.notExists(DATA_DIR)) {
                Files.createDirectories(DATA_DIR);
                return;
            }

            Path sourceFile = resolveLoadFile();
            if (sourceFile == null || Files.notExists(sourceFile) || Files.size(sourceFile) == 0) {
                return;
            }

            try (Reader reader = Files.newBufferedReader(sourceFile, StandardCharsets.UTF_8)) {
                Type listType = new TypeToken<List<Task>>() {}.getType();
                List<Task> loadedTasks = gson.fromJson(reader, listType);

                if (loadedTasks != null) {
                    for (Task t : loadedTasks) {
                        if (t != null) {
                            if (t.getRemindTime() == null) {
                                t.setRemindTime(LocalTime.of(9, 0));
                            }
                            if (t.getId() == null || t.getId().isBlank()) {
                                t.setId(null);
                            }
                            if (t.getCreatedAt() == null) {
                                t.setCreatedAt(LocalDateTime.now());
                            }
                            if (!t.isCompleted()) {
                                t.setCompletedAt(null);
                            }
                            t.setSubTasks(t.getSubTasks());
                        }
                    }
                    taskList.setAll(loadedTasks);
                }
            }
        } catch (IOException | JsonIOException | JsonSyntaxException e) {
            e.printStackTrace();
        }
    }

    private Path resolveLoadFile() throws IOException {
        if (Files.exists(DATA_FILE)) {
            return DATA_FILE;
        }

        try (Stream<Path> paths = Files.list(DATA_DIR)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .max(Path::compareTo)
                    .orElse(null);
        }
    }

    //联动checkbox，用于标记完成后直接保存
    public void setCompleted(Task task, boolean completed) {
        if (task != null) {
            task.setCompleted(completed);
            if (task.hasSubTasks()) {
                task.getSubTasks().forEach(subTask -> subTask.setCompleted(completed));
            }
            saveTasks();
        }
    }

    public void setSubTaskCompleted(Task task, Task.SubTask subTask, boolean completed) {
        if (task == null || subTask == null) {
            return;
        }
        subTask.setCompleted(completed);
        task.syncCompletionWithSubTasks();
        saveTasks();
    }

    public void addTask(Task task) {
        if (task != null) {
            taskList.add(task);
            saveTasks();
        }
    }

    public void removeTask(Task task) {
        if (task != null) {
            taskList.remove(task);
            saveTasks();
        }
    }

    public void updateTask(Task oldTask, Task newTaskData) {
        if (oldTask == null || newTaskData == null) {
            return;
        }
        oldTask.setTitle(newTaskData.getTitle());
        oldTask.setDescription(newTaskData.getDescription());
        oldTask.setDueTime(newTaskData.getDueTime());
        oldTask.setRemindTime(newTaskData.getRemindTime());
        oldTask.setPriority(newTaskData.getPriority());
        oldTask.setSubTasks(newTaskData.getSubTasks());
        if (!oldTask.hasSubTasks()) {
            oldTask.setCompleted(newTaskData.isCompleted());
        }
        saveTasks();
    }

    public void toggleCompleted(Task task) {//
        if (task != null) {
            task.setCompleted(!task.isCompleted());
            saveTasks();
        }
    }
}
