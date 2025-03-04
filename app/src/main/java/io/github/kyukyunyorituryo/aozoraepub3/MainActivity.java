package io.github.kyukyunyorituryo.aozoraepub3;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        Button buttonOpen = findViewById(R.id.button_open);
        Button buttonProcess = findViewById(R.id.button_process);
        Button buttonSave = findViewById(R.id.button_save);

        buttonOpen.setOnClickListener(v -> openFilePicker());
        buttonProcess.setOnClickListener(v -> processFile());
        buttonSave.setOnClickListener(v -> openFileSaver());
    }
    // 🔹 ファイル選択 UI を開く (SAF)
    private final ActivityResultLauncher<Intent> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        copyFileToInternalStorage(uri);
                    }
                }
            });

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*"); // すべてのファイルを選択可能
        filePickerLauncher.launch(intent);
    }

    // 🔹 選択したファイルを内部ストレージにコピー
    private void copyFileToInternalStorage(Uri uri) {
        File internalFile = new File(getFilesDir(), "input.txt");

        try {
            Files.copy(getContentResolver().openInputStream(uri), internalFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Toast.makeText(this, "ファイルを内部ストレージにコピーしました", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "コピーに失敗しました", Toast.LENGTH_SHORT).show();
        }
    }
    private void processFile() {
        File inputFile = new File(getFilesDir(), "input.txt");
        File outputFile = new File(getFilesDir(), "output.txt");

        if (!inputFile.exists() || inputFile.length() == 0) {
            Toast.makeText(this, "入力ファイルが存在しないか空です", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // 🔹 ファイルを読み込んで大文字変換し、新しいファイルに保存
            String content = new String(Files.readAllBytes(inputFile.toPath()));
            Files.write(outputFile.toPath(), content.toUpperCase().getBytes());

            Toast.makeText(this, "ファイルを処理しました", Toast.LENGTH_SHORT).show();

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "処理に失敗しました", Toast.LENGTH_SHORT).show();
        }
    }
    // 🔹 SAF で保存先を選択する
    private final ActivityResultLauncher<Intent> saveFileLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        saveFileToUri(uri);
                    }
                }
            });

    private void openFileSaver() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, "output.txt");
        saveFileLauncher.launch(intent);
    }

    // 🔹 内部ストレージのファイルを SAF で保存
    private void saveFileToUri(Uri uri) {
        File internalFile = new File(getFilesDir(), "output.txt");

        if (!internalFile.exists() || internalFile.length() == 0) {
            Toast.makeText(this, "出力ファイルが空のため保存できません", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Files.copy(internalFile.toPath(), getContentResolver().openOutputStream(uri));
            Toast.makeText(this, "ファイルを保存しました", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "保存に失敗しました", Toast.LENGTH_SHORT).show();
        }
    }

}
