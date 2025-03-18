package io.github.kyukyunyorituryo.aozoraepub3;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
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

import io.github.kyukyunyorituryo.aozoraepub3.converter.AozoraEpub3Converter;
import io.github.kyukyunyorituryo.aozoraepub3.converter.AozoraGaijiConverter;
import io.github.kyukyunyorituryo.aozoraepub3.converter.JisConverter;
import io.github.kyukyunyorituryo.aozoraepub3.util.CharUtils;
import io.github.kyukyunyorituryo.aozoraepub3.util.LogAppender;
import io.github.kyukyunyorituryo.aozoraepub3.writer.Epub3Writer;
import io.github.kyukyunyorituryo.aozoraepub3.converter.LatinConverter;

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
        // ログを表示するTextViewを取得
        TextView logTextView = findViewById(R.id.textViewLog);

        // LogAppenderにTextViewをセット
        LogAppender.setTextView(logTextView);

        // ログの出力テスト
        LogAppender.println("アプリ起動");
        LogAppender.info(42, "情報ログ", "詳細情報");
        LogAppender.warn(100, "警告ログ");
        LogAppender.error(200, "エラーログ", "エラー詳細");
        String tst="５５"+"ＷＷ";
        LogAppender.println(CharUtils.fullToHalf(tst)+tst);

        Epub3Writer writer = new Epub3Writer("");
        AozoraEpub3Converter converter = null;
        try {
            converter = new AozoraEpub3Converter(writer, this);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        String str;
        try {
            str = converter.convertTitleLineToEpub3(converter.convertGaijiChuki("｜ルビ※［＃米印］《るび》※［＃米印］※［＃始め二重山括弧］※［＃終わり二重山括弧］", true, true));
            System.out.println(str);
            String line = converter.convertGaijiChuki("外字の後のルビ※［＃（外字.tif）］《がいじ》", true, true);
            System.out.println(line);

            line = converter.convertGaijiChuki("外字の後の｜ルビ※［＃（外字.tif）］《がいじ》", true, true);
            System.out.println(line);

            line = converter.convertGaijiChuki("※［＃（外字.tif）］《がいじ》", true, true);
            System.out.println(line);
            line = converter.convertGaijiChuki("外字の後の｜ルビ《るび》※［＃（外字.tif）］《るび》", true, true);
            System.out.println(line);

            line = converter.convertGaijiChuki("その上方に※［＃逆三角形と三角形が向き合っている形（fig1317_26.png、横26×縦59）入る］《デアボロ》", true, true);
            System.out.println(line);
            converter.vertical = true;
            StringBuilder buf;
            buf = converter.convertRubyText("※《29※》");
            System.out.println(buf);
            buf = converter.convertRubyText("※※＃※》");
            System.out.println(buf);

            buf = converter.convertRubyText("｜※｜縦線《たてせん》※｜");
            System.out.println(buf);
            //Assert.assertEquals(buf.toString(), "<ruby>｜縦線<rt>たてせん</rt></ruby>｜");
            buf = converter.convertRubyText("※｜縦線《たてせん》※｜");
            System.out.println(buf);
            //Assert.assertEquals(buf.toString(), "｜<ruby>縦線<rt>たてせん</rt></ruby>｜");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String jarPath ="";

        AozoraGaijiConverter gaijiconverter;
        try {
            gaijiconverter = new AozoraGaijiConverter(this);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println(gaijiconverter.codeToCharString("U+0041"));
        System.out.println(gaijiconverter.codeToCharString("U+04E02"));
        System.out.println(gaijiconverter.toAlterString("感嘆符三つ"));
        JisConverter gconverter = JisConverter.getConverter();
        System.out.println(gconverter.toCharString(0, 0, 1)); // !
        System.out.println(gconverter.toCharString(1, 4, 87)); // か゚
        System.out.println(gconverter.toCharString(1, 12, 90)); // null
        System.out.println(gconverter.toCharString(1, 13, 94)); // ☞
        System.out.println(gconverter.toCharString(1, 14, 1)); // 俱
        System.out.println(gconverter.toCharString(1, 16, 1)); // 亜
        System.out.println(gconverter.toCharString(2, 94, 64)); // 䵷
        System.out.println(gconverter.toCharString(2, 94, 86)); // 𪚲
        System.out.println(gconverter.toCharString(1, 90, 16)); // 縈 1-90-16
        System.out.println(gconverter.toCharString(2, 94, 85));
        LatinConverter latinConverter = null;
        try {
            latinConverter = new LatinConverter(this);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println(latinConverter.toLatinCharacter("A&") );
        System.out.println(latinConverter.toLatinCharacter("A`") );
        System.out.println(latinConverter.toLatinCharacter("A\'") );

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
