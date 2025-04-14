package io.github.kyukyunyorituryo.aozoraepub3;

import static io.github.kyukyunyorituryo.aozoraepub3.AozoraEpub3.getOutFile;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.method.ScrollingMovementMethod;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import com.github.junrar.exception.RarException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serial;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.kyukyunyorituryo.aozoraepub3.converter.AozoraEpub3Converter;
import io.github.kyukyunyorituryo.aozoraepub3.image.ImageInfoReader;
import io.github.kyukyunyorituryo.aozoraepub3.info.BookInfo;
import io.github.kyukyunyorituryo.aozoraepub3.info.BookInfoHistory;
import io.github.kyukyunyorituryo.aozoraepub3.info.SectionInfo;
import io.github.kyukyunyorituryo.aozoraepub3.util.LogAppender;
import io.github.kyukyunyorituryo.aozoraepub3.writer.Epub3ImageWriter;
import io.github.kyukyunyorituryo.aozoraepub3.writer.Epub3Writer;
import io.github.kyukyunyorituryo.aozoraepub3.web.WebAozoraConverter;
public class MainActivity extends AppCompatActivity {
    private File srcFile;
    private Properties props;
    private File outFile;

    //プログレスバー
    ProgressBar jProgressBar;

    /** 青空→ePub3変換クラス */
    AozoraEpub3Converter aozoraConverter;

    /** Web小説青空変換クラス */
    WebAozoraConverter webConverter;

    /** ePub3出力クラス */
    Epub3Writer epub3Writer;

    /** ePub3画像出力クラス */
    Epub3ImageWriter epub3ImageWriter;

    /** 変換をキャンセルした場合true */
    boolean convertCanceled = false;
    /** 変換実行中 */
    boolean running = false;

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
        logTextView.setMovementMethod(new ScrollingMovementMethod());

        // LogAppenderにTextViewをセット
        LogAppender.setTextView(logTextView);

        // ログの出力テスト
        LogAppender.println("AozoraEpub3: "+AozoraEpub3.VERSION);
        LogAppender.append("  ( VM specification version "+System.getProperty("java.specification.version"));
        LogAppender.append("  /  "+System.getProperty("os.name"));
        LogAppender.append(" )\n対応ファイル: 青空文庫txt(txt,zip,rar), 画像(zip,rar,cbz)");

        //preferenceの取得
        getPreferenceValues();

        //プロパティーの読み込み
        props = new Properties();
        try {
            InputStream isini = this.getAssets().open("AozoraEpub3.ini");
            props.load(isini);
        } catch (IOException e) {
            e.printStackTrace();
        }

        Button buttonCover = findViewById(R.id.coverButton);
        Button buttonFigure = findViewById(R.id.figureButton);
        Button buttonOpen = findViewById(R.id.button_open);
        Button buttonProcess = findViewById(R.id.button_process);
        Button buttonSave = findViewById(R.id.button_save);
        Button buttonSetting =findViewById(R.id.openSettingsButton);

        buttonCover.setOnClickListener(v ->coverFilePicker());
        buttonFigure.setOnClickListener( v -> figureFilePicker());
        buttonOpen.setOnClickListener(v -> openFilePicker());
        buttonProcess.setOnClickListener(v -> processFile());
        buttonSave.setOnClickListener(v -> openFileSaver());
        buttonSetting.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });
        // Intent から URL を受け取る
        handleIntent(getIntent());
    }
    //preferenceの取得
    private void getPreferenceValues() {

        // 設定値を取得する例
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String titleType = prefs.getString("title_type", "0");
        boolean pubFirst = prefs.getBoolean("pub_first", false);
        boolean useFilename = prefs.getBoolean("use_filename", false);
        int maxCoverLines;
        try {
            maxCoverLines = Integer.parseInt(prefs.getString("max_cover_lines", "10"));
        } catch (NumberFormatException e) {
            maxCoverLines = 10;
        }

        String coverMode = prefs.getString("cover_mode", "0");
        boolean useCoverHistory = prefs.getBoolean("use_cover_history", true);
        boolean outputCoverPage = prefs.getBoolean("output_cover_page", true);
        boolean outputTitlePage = prefs.getBoolean("output_title_page", true);
        String titlePageStyle = prefs.getString("title_page_style", "middle");
        boolean outputToc = prefs.getBoolean("output_toc", true);
        String tocStyle = prefs.getString("toc_style", "vertical");
        String fileExtension = prefs.getString("file_extension", ".epub");
        boolean useTitleForFilename = prefs.getBoolean("use_title_for_filename", true);
        boolean overwriteOutput = prefs.getBoolean("overwrite_output", true);

        // 出力先
        boolean samePath = prefs.getBoolean("same_path", true);
        String outputPath = prefs.getString("output_path", "");
        // 変換設定
        String inputEncoding = prefs.getString("input_encoding", "AUTO");
        String language = prefs.getString("language", "ja");
        String textDirection = prefs.getString("text_direction", "vertical");
        // 挿絵除外
        boolean excludeIllustrations = prefs.getBoolean("exclude_illustrations", false);
        // 画面・表紙サイズ
        int screenWidth = Integer.parseInt(prefs.getString("screen_width", "1600"));
        int screenHeight = Integer.parseInt(prefs.getString("screen_height", "2560"));
        int coverWidth = Integer.parseInt(prefs.getString("cover_width", "0"));
        int coverHeight = Integer.parseInt(prefs.getString("cover_height", "0"));
        // 画像倍率
        boolean imageScaleEnabled = prefs.getBoolean("image_scale_enabled", true);
        float imageScaleFactor = Float.parseFloat(prefs.getString("image_scale_factor", "1.0"));


        // 画像回り込み
        boolean imageFloatEnabled = prefs.getBoolean("image_float_enabled", false);
        int imageFloatWidth = Integer.parseInt(prefs.getString("image_float_width", "600"));
        int imageFloatHeight = Integer.parseInt(prefs.getString("image_float_height", "400"));
        String imageFloatPosition = prefs.getString("image_float_position", "上/左");

        // 画像単ページ化
        int singlePageWidth = Integer.parseInt(prefs.getString("single_page_width", "200"));
        int singlePageHeight = Integer.parseInt(prefs.getString("single_page_height", "300"));
        int singlePageWidthOnly = Integer.parseInt(prefs.getString("single_page_width_only", "300"));
        String imageSizeMode = prefs.getString("image_size_mode", "none");
        boolean fitImage = prefs.getBoolean("fit_image", true);

        // Float指定 (Readerのみ)
        boolean floatImageSinglePage = prefs.getBoolean("float_image_single_page", false);
        boolean floatImageBlock = prefs.getBoolean("float_image_block", false);

        // 全画面表示
        boolean outputSvg = prefs.getBoolean("output_svg", false);

        // Jpeg圧縮率
        int jpegQuality = Integer.parseInt(prefs.getString("jpeg_quality", "100")); // default: 100

        // 色調整
        boolean gammaCorrection = prefs.getBoolean("gamma_correction", false);
        float gammaValue = Float.parseFloat(prefs.getString("gamma_value", "1.0")); // default: 1.0

        // --- 余白除去 ---
        boolean autoMarginEnabled = prefs.getBoolean("auto_margin_enabled", false);
        int autoMarginLimitH = Integer.parseInt(prefs.getString("auto_margin_limit_h", "15"));
        int autoMarginLimitV = Integer.parseInt(prefs.getString("auto_margin_limit_v", "15"));
        float autoMarginPadding = Float.parseFloat(prefs.getString("auto_margin_padding", "1.0"));
        int autoMarginWhiteLevel = Integer.parseInt(prefs.getString("auto_margin_white_level", "80"));
        String autoMarginNombrePosition = prefs.getString("auto_margin_nombre_position", "none");
        float autoMarginNombreSize = Float.parseFloat(prefs.getString("auto_margin_nombre_size", "3.0"));

        // --- 文中全角スペースの処理 ---
        String spaceHandling = prefs.getString("space_handling", "default");

        // --- 「○○」に「××」の注記 ---
        String annotationHandling = prefs.getString("annotation_handling", "0");

        // --- 自動縦中横 ---
        boolean enableAutoYoko = prefs.getBoolean("enable_auto_yoko", true);
        boolean autoYokoNum1 = prefs.getBoolean("auto_yoko_num1", false);
        boolean autoYokoNum3 = prefs.getBoolean("auto_yoko_num3", false);
        boolean autoEq1 = prefs.getBoolean("auto_eq1", false);

        // --- コメントブロック出力 ---
        boolean commentOutput = prefs.getBoolean("comment_output", false);
        boolean commentConvert = prefs.getBoolean("comment_convert", false);


        // --- 栞用ID ---
        boolean markId = prefs.getBoolean("mark_id", false);

        // --- 空行除去 ---
        String removeEmptyLine = prefs.getString("remove_empty_line", "0");
        String maxEmptyLine = prefs.getString("max_empty_line", "-");

        // --- 行頭字下げ ---
        boolean forceIndent = prefs.getBoolean("force_indent", false);

        // --- 強制改ページ ---
        boolean pageBreakEnabled = prefs.getBoolean("page_break_enabled", true);
        int pageBreakSizeKb = Integer.parseInt(prefs.getString("page_break_size_kb", "400"));

        boolean pageBreakEmptyEnabled = prefs.getBoolean("page_break_empty_enabled", false);
        String pageBreakEmptyLineCount = prefs.getString("page_break_empty_line_count", "2");
        int pageBreakEmptySizeKb = Integer.parseInt(prefs.getString("page_break_empty_size_kb", "300"));

        boolean pageBreakChapterEnabled = prefs.getBoolean("page_break_chapter_enabled", false);
        int pageBreakChapterSizeKb = Integer.parseInt(prefs.getString("page_break_chapter_size_kb", "200"));


        // --- 目次設定 ---
        int maxChapterNameLength = Integer.parseInt(prefs.getString("max_chapter_name_length", "64"));

        boolean tocIncludeCover = prefs.getBoolean("toc_include_cover", false);
        boolean tocIncludeTitle = prefs.getBoolean("toc_include_title", true);
        boolean tocJoinNextLine = prefs.getBoolean("toc_join_next_line", false);
        boolean tocExcludeRepeated = prefs.getBoolean("toc_exclude_repeated", true);
        boolean navNested = prefs.getBoolean("nav_nested", true);
        boolean ncxNested = prefs.getBoolean("ncx_nested", true);

        // --- 目次抽出 ---
        boolean chapterHead = prefs.getBoolean("chapter_head", true);
        boolean chapterHead1 = prefs.getBoolean("chapter_head1", true);
        boolean chapterHead2 = prefs.getBoolean("chapter_head2", true);
        boolean chapterHead3 = prefs.getBoolean("chapter_head3", true);
        boolean chapterSameLine = prefs.getBoolean("chapter_same_line", false);
        boolean chapterAfterPagebreak = prefs.getBoolean("chapter_after_pagebreak", true);
        boolean chapterName = prefs.getBoolean("chapter_name", true);


        // --- 数字系 ---
        boolean chapterNumOnly = prefs.getBoolean("chapter_num_only", false);
        boolean chapterNumTitle = prefs.getBoolean("chapter_num_title", false);
        boolean chapterNumParen = prefs.getBoolean("chapter_num_paren", false);
        boolean chapterNumParenTitle = prefs.getBoolean("chapter_num_paren_title", false);
        boolean chapterPatternEnabled = prefs.getBoolean("chapter_pattern_enabled", false);
        String chapterPattern = prefs.getString("chapter_pattern", "^(見出し１|見出し２|見出し３)$");

        // --- スタイル ---
        float lineHeight = Float.parseFloat(prefs.getString("line_height", "1.8"));
        int fontSize = Integer.parseInt(prefs.getString("font_size", "100"));

        // --- 太字ゴシック表示 ---
        boolean boldNote = prefs.getBoolean("bold_note", false);
        boolean gothicNote = prefs.getBoolean("gothic_note", false);

        // --- テキスト余白 ---
        float pageMarginTop = Float.parseFloat(prefs.getString("page_margin_top", "0.5"));
        float pageMarginRight = Float.parseFloat(prefs.getString("page_margin_right", "0.5"));
        float pageMarginBottom = Float.parseFloat(prefs.getString("page_margin_bottom", "0.5"));
        float pageMarginLeft = Float.parseFloat(prefs.getString("page_margin_left", "0.5"));
        String pageMarginUnit = prefs.getString("page_margin_unit", "char");


        // --- テキスト余白 (html margin) Reader用 ---
        float bodyMarginTop = Float.parseFloat(prefs.getString("body_margin_top", "0"));
        float bodyMarginRight = Float.parseFloat(prefs.getString("body_margin_right", "0"));
        float bodyMarginBottom = Float.parseFloat(prefs.getString("body_margin_bottom", "0"));
        float bodyMarginLeft = Float.parseFloat(prefs.getString("body_margin_left", "0"));
        String bodyMarginUnit = prefs.getString("body_margin_unit", "char");

        // --- 文字出力 ---
        String dakutenOutputType = prefs.getString("dakuten_output_type", "font");

        // --- IVS出力 ---
        boolean ivsBmp = prefs.getBoolean("ivs_bmp", false);
        boolean ivsSsp = prefs.getBoolean("ivs_ssp", false);

        // --- Web取得設定 ---
        float webInterval = Float.parseFloat(prefs.getString("web_interval", "0.5"));

        // --- UA設定 ---
        String uaType = prefs.getString("ua_type", "");

        // --- Web画像設定 ---
        boolean webLargeImage = prefs.getBoolean("web_large_image", false);

        // --- キャッシュ保存パス ---
        String cachePath = prefs.getString("cache_path", ".cache");

        // --- 更新判定 ---
        int webModifiedExpire = Integer.parseInt(prefs.getString("web_modified_expire", "24"));


        // --- ePub出力設定 ---
        boolean webConvertUpdatedOnly = prefs.getBoolean("web_convert_updated_only", false);

        // --- 変換対象 ---
        boolean webLatestOnly = prefs.getBoolean("web_latest_only", false);
        int webLatestCount = Integer.parseInt(prefs.getString("web_latest_count", "1"));
        boolean webModifiedOnly = prefs.getBoolean("web_modified_only", false);
        boolean webModifiedTailOnly = prefs.getBoolean("web_modified_tail_only", false);





    }
    /** Intent から URL を取得して処理 */
    private void handleIntent(Intent intent) {
        if (intent == null) return;

        String receivedUrl = null;

        // 共有 (SEND) インテントからの取得
        if (Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {
            receivedUrl = intent.getStringExtra(Intent.EXTRA_TEXT);
        }

        // リンクを開く (VIEW) インテントからの取得
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri data = intent.getData();
            if (data != null) {
                receivedUrl = data.toString();
            }
        }

        // URL が取得できた場合、変換処理を実行
        if (receivedUrl != null && !receivedUrl.isEmpty()) {
            LogAppender.println("受け取ったURL: " + receivedUrl);

            // URLリストに追加
            List<String> urlList = new ArrayList<>();
            urlList.add(receivedUrl);

            // 出力先フォルダ（キャッシュフォルダを使用）
            File dstPath = getCacheDir();

            // 変換処理を実行
            convertWeb(urlList, new ArrayList<>(), dstPath);
        } else {
            LogAppender.println("URLが空または無効です");
        }
    }
    private void figureFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        String[] mimeTypes ={
                "image/jpeg",
                "image/png"};
        intent.setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        filePickerLauncher.launch(intent);
    }

    private void coverFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        String[] mimeTypes ={
                "image/jpeg",
                "image/png"};
        intent.setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        filePickerLauncher.launch(intent);
    }

    // 🔹 ファイル選択 UI を開く (SAF) - 複数ファイル対応
    private final ActivityResultLauncher<Intent> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Intent data = result.getData();
                    List<Uri> uriList = new ArrayList<>();

                    // 複数ファイル選択
                    if (data.getClipData() != null) {
                        ClipData clipData = data.getClipData();
                        for (int i = 0; i < clipData.getItemCount(); i++) {
                            uriList.add(clipData.getItemAt(i).getUri());
                        }
                    }
                    // 単一ファイル選択
                    else if (data.getData() != null) {
                        uriList.add(data.getData());
                    }

                    // 選択されたすべてのファイルを処理
                    for (Uri uri : uriList) {
                        copyFileToInternalStorage(uri);
                    }
                }
            });

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        String[] mimeTypes ={
                "text/plain",
                "application/zip"};
        intent.setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        filePickerLauncher.launch(intent);
    }

    // 🔹 選択したファイルを内部ストレージにコピー
    private void copyFileToInternalStorage(Uri uri) {
        File src = null;
        String[] projection = {MediaStore.MediaColumns.DISPLAY_NAME};
        String path = null;
        Cursor cursor = this.getContentResolver().query(uri, projection, null, null, null);
        if (cursor != null) {

            if (cursor.moveToFirst()) {
                path = cursor.getString(0);
            }
            cursor.close();
            Context context = getApplicationContext();
            src = new File(context.getFilesDir(), path);

            System.out.println("filename:" + path);
            System.out.println("filename:" + src.getPath());
        }

        try {
            Files.copy(getContentResolver().openInputStream(uri), src.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Toast.makeText(this, "ファイルを内部ストレージにコピーしました", Toast.LENGTH_SHORT).show();
            if (path.endsWith(".txt") || path.endsWith(".zip")) {
               srcFile= src;
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "コピーに失敗しました", Toast.LENGTH_SHORT).show();
        }
    }
    private void processFile() {
        //File inputFile = new File(getFilesDir(), "input.txt");
        //File outputFile = new File(getFilesDir(), "output.txt");

        if (!srcFile.exists() || srcFile.length() == 0) {
            Toast.makeText(this, "入力ファイルが存在しないか空です", Toast.LENGTH_SHORT).show();
            return;
        }
        /** ePub3出力クラス */
        Epub3Writer epub3Writer;
        /** ePub3画像出力クラス */
        Epub3ImageWriter epub3ImageWriter;

        /** 出力先パス */
        File dstPath = null;
        //ePub出力クラス初期化
        epub3Writer = new Epub3Writer(this);
        epub3ImageWriter = new Epub3ImageWriter(this);
        int titleIndex = 0; //try { titleIndex = Integer.parseInt(props.getProperty("TitleType")); } catch (Exception e) {}//表題
        //コマンドラインオプション以外
        boolean coverPage = "1".equals(props.getProperty("CoverPage"));//表紙追加
        int titlePage = BookInfo.TITLE_NONE;
        if ("1".equals(props.getProperty("TitlePageWrite"))) {
            try { titlePage =Integer.parseInt(props.getProperty("TitlePage")); } catch (Exception e) {}
        }

        boolean withMarkId = "1".equals(props.getProperty("MarkId"));
        //boolean gaiji32 = "1".equals(props.getProperty("Gaiji32"));
        boolean commentPrint = "1".equals(props.getProperty("CommentPrint"));
        boolean commentConvert = "1".equals(props.getProperty("CommentConvert"));
        boolean autoYoko = "1".equals(props.getProperty("AutoYoko"));
        boolean autoYokoNum1 = "1".equals(props.getProperty("AutoYokoNum1"));
        boolean autoYokoNum3 = "1".equals(props.getProperty("AutoYokoNum3"));
        boolean autoYokoEQ1 = "1".equals(props.getProperty("AutoYokoEQ1"));
        int spaceHyp = 0; try { spaceHyp = Integer.parseInt(props.getProperty("SpaceHyphenation")); } catch (Exception e) {}
        boolean tocPage = "1".equals(props.getProperty("TocPage"));//目次追加
        boolean tocVertical = "1".equals(props.getProperty("TocVertical"));//目次縦書き
        boolean coverPageToc = "1".equals(props.getProperty("CoverPageToc"));
        int removeEmptyLine = 0; try { removeEmptyLine = Integer.parseInt(props.getProperty("RemoveEmptyLine")); } catch (Exception e) {}
        int maxEmptyLine = 0; try { maxEmptyLine = Integer.parseInt(props.getProperty("MaxEmptyLine")); } catch (Exception e) {}

        //画面サイズと画像リサイズ
        int dispW = 600; try { dispW =Integer.parseInt(props.getProperty("DispW")); } catch (Exception e) {}
        int dispH = 800; try { dispH =Integer.parseInt(props.getProperty("DispH")); } catch (Exception e) {}
        int coverW = 600; try { coverW = Integer.parseInt(props.getProperty("CoverW")); } catch (Exception e) {}
        int coverH = 800; try { coverH = Integer.parseInt(props.getProperty("CoverH")); } catch (Exception e) {}
        int resizeW = 0; if ("1".equals(props.getProperty("ResizeW"))) try { resizeW = Integer.parseInt(props.getProperty("ResizeNumW")); } catch (Exception e) {}
        int resizeH = 0; if ("1".equals(props.getProperty("ResizeH"))) try { resizeH = Integer.parseInt(props.getProperty("ResizeNumH")); } catch (Exception e) {}
        int singlePageSizeW = 480; try { singlePageSizeW = Integer.parseInt(props.getProperty("SinglePageSizeW")); } catch (Exception e) {}
        int singlePageSizeH = 640; try { singlePageSizeH = Integer.parseInt(props.getProperty("SinglePageSizeH")); } catch (Exception e) {}
        int singlePageWidth = 600; try { singlePageWidth = Integer.parseInt(props.getProperty("SinglePageWidth")); } catch (Exception e) {}
        float imageScale = 1; try { imageScale = Float.parseFloat(props.getProperty("ImageScale")); } catch (Exception e) {}
        int imageFloatType = 0; try { imageFloatType = Integer.parseInt(props.getProperty("ImageFloatType")); } catch (Exception e) {}
        int imageFloatW = 0; try { imageFloatW = Integer.parseInt(props.getProperty("ImageFloatW")); } catch (Exception e) {}
        int imageFloatH = 0; try { imageFloatH = Integer.parseInt(props.getProperty("ImageFloatH")); } catch (Exception e) {}
        int imageSizeType = SectionInfo.IMAGE_SIZE_TYPE_HEIGHT; try { imageSizeType = Integer.parseInt(props.getProperty("ImageSizeType")); } catch (Exception e) {}
        boolean fitImage = "1".equals(props.getProperty("FitImage"));
        boolean svgImage = "1".equals(props.getProperty("SvgImage"));
        int rotateImage = 0; if ("1".equals(props.getProperty("RotateImage"))) rotateImage = 90; else if ("2".equals(props.getProperty("RotateImage"))) rotateImage = -90;
        float jpegQualty = 0.8f; try { jpegQualty = Integer.parseInt(props.getProperty("JpegQuality"))/100f; } catch (Exception e) {}
        float gamma = 1.0f; if ( "1".equals(props.getProperty("Gamma"))) try { gamma = Float.parseFloat(props.getProperty("GammaValue")); } catch (Exception e) {}
        int autoMarginLimitH = 0;
        int autoMarginLimitV = 0;
        int autoMarginWhiteLevel = 80;
        float autoMarginPadding = 0;
        int autoMarginNombre = 0;
        float nobreSize = 0.03f;
        if ("1".equals(props.getProperty("AutoMargin"))) {
            try { autoMarginLimitH = Integer.parseInt(props.getProperty("AutoMarginLimitH")); } catch (Exception e) {}
            try { autoMarginLimitV = Integer.parseInt(props.getProperty("AutoMarginLimitV")); } catch (Exception e) {}
            try { autoMarginWhiteLevel = Integer.parseInt(props.getProperty("AutoMarginWhiteLevel")); } catch (Exception e) {}
            try { autoMarginPadding = Float.parseFloat(props.getProperty("AutoMarginPadding")); } catch (Exception e) {}
            try { autoMarginNombre = Integer.parseInt(props.getProperty("AutoMarginNombre")); } catch (Exception e) {}
            try { autoMarginPadding = Float.parseFloat(props.getProperty("AutoMarginNombreSize")); } catch (Exception e) {}
        }

        epub3Writer.setImageParam(dispW, dispH, coverW, coverH, resizeW, resizeH, singlePageSizeW, singlePageSizeH, singlePageWidth, imageSizeType, fitImage, svgImage, rotateImage,
                imageScale, imageFloatType, imageFloatW, imageFloatH, jpegQualty, gamma, autoMarginLimitH, autoMarginLimitV, autoMarginWhiteLevel, autoMarginPadding, autoMarginNombre, nobreSize);
        epub3ImageWriter.setImageParam(dispW, dispH, coverW, coverH, resizeW, resizeH, singlePageSizeW, singlePageSizeH, singlePageWidth, imageSizeType, fitImage, svgImage, rotateImage,
                imageScale, imageFloatType, imageFloatW, imageFloatH, jpegQualty, gamma, autoMarginLimitH, autoMarginLimitV, autoMarginWhiteLevel, autoMarginPadding, autoMarginNombre, nobreSize);
        //目次階層化設定
        epub3Writer.setTocParam("1".equals(props.getProperty("NavNest")), "1".equals(props.getProperty("NcxNest")));

        //スタイル設定
        String[] pageMargin = {};
        try { pageMargin = props.getProperty("PageMargin").split(","); } catch (Exception e) {}
        if (pageMargin.length != 4) pageMargin = new String[]{"0", "0", "0", "0"};
        else {
            String pageMarginUnit = props.getProperty("PageMarginUnit");
            for (int i=0; i<4; i++) { pageMargin[i] += pageMarginUnit; }
        }
        String[] bodyMargin = {};
        try { bodyMargin = props.getProperty("BodyMargin").split(","); } catch (Exception e) {}
        if (bodyMargin.length != 4) bodyMargin = new String[]{"0", "0", "0", "0"};
        else {
            String bodyMarginUnit = props.getProperty("BodyMarginUnit");
            for (int i=0; i<4; i++) { bodyMargin[i] += bodyMarginUnit; }
        }
        float lineHeight = 1.8f; try { lineHeight = Float.parseFloat(props.getProperty("LineHeight")); } catch (Exception e) {}
        int fontSize = 100; try { fontSize = Integer.parseInt(props.getProperty("FontSize")); } catch (Exception e) {}
        boolean boldUseGothic = "1".equals(props.getProperty("BoldUseGothic"));
        boolean gothicUseBold = "1".equals(props.getProperty("gothicUseBold"));
        epub3Writer.setStyles(pageMargin, bodyMargin, lineHeight, fontSize, boldUseGothic, gothicUseBold);



        //自動改ページ
        int forcePageBreakSize = 0;
        int forcePageBreakEmpty = 0;
        int forcePageBreakEmptySize = 0;
        int forcePageBreakChapter = 0;
        int forcePageBreakChapterSize = 0;
        if ("1".equals(props.getProperty("PageBreak"))) {
            try {
                try { forcePageBreakSize = Integer.parseInt(props.getProperty("PageBreakSize")) * 1024; } catch (Exception e) {}
                if ("1".equals(props.getProperty("PageBreakEmpty"))) {
                    try { forcePageBreakEmpty = Integer.parseInt(props.getProperty("PageBreakEmptyLine")); } catch (Exception e) {}
                    try { forcePageBreakEmptySize = Integer.parseInt(props.getProperty("PageBreakEmptySize")) * 1024; } catch (Exception e) {}
                } if ("1".equals(props.getProperty("PageBreakChapter"))) {
                    forcePageBreakChapter = 1;
                    try { forcePageBreakChapterSize = Integer.parseInt(props.getProperty("PageBreakChapterSize")) * 1024; } catch (Exception e) {}
                }
            } catch (Exception e) {}
        }
        int maxLength = 64; try { maxLength = Integer.parseInt((props.getProperty("ChapterNameLength"))); } catch (Exception e) {}
        boolean insertTitleToc = "1".equals(props.getProperty("TitleToc"));
        boolean chapterExclude = "1".equals(props.getProperty("ChapterExclude"));
        boolean chapterUseNextLine = "1".equals(props.getProperty("ChapterUseNextLine"));
        boolean chapterSection = !props.containsKey("ChapterSection")||"1".equals(props.getProperty("ChapterSection"));
        boolean chapterH = "1".equals(props.getProperty("ChapterH"));
        boolean chapterH1 = "1".equals(props.getProperty("ChapterH1"));
        boolean chapterH2 = "1".equals(props.getProperty("ChapterH2"));
        boolean chapterH3 = "1".equals(props.getProperty("ChapterH3"));
        boolean sameLineChapter = "1".equals(props.getProperty("SameLineChapter"));
        boolean chapterName = "1".equals(props.getProperty("ChapterName"));
        boolean chapterNumOnly = "1".equals(props.getProperty("ChapterNumOnly"));
        boolean chapterNumTitle = "1".equals(props.getProperty("ChapterNumTitle"));
        boolean chapterNumParen = "1".equals(props.getProperty("ChapterNumParen"));
        boolean chapterNumParenTitle = "1".equals(props.getProperty("hapterNumParenTitle"));
        String chapterPattern = ""; if ("1".equals(props.getProperty("ChapterPattern"))) chapterPattern = props.getProperty("ChapterPatternText");

        //オプション指定を反映
        boolean useFileName = false;//表題に入力ファイル名利用
        String coverFileName = null;
        String encType = "AUTO";//文字コードの初期設定を空に
        String outExt = ".epub";
        boolean autoFileName = true; //ファイル名を表題に利用
        boolean vertical = true;
        String targetDevice = null;

        //変換クラス生成とパラメータ設定
        AozoraEpub3Converter aozoraConverter = null;
        try {
            aozoraConverter = new AozoraEpub3Converter(epub3Writer, this);
        } catch (IOException e) {
            e.printStackTrace();
        }
        //挿絵なし
        aozoraConverter.setNoIllust("1".equals(props.getProperty("NoIllust")));

        //栞用span出力
        aozoraConverter.setWithMarkId(withMarkId);
        //変換オプション設定
        aozoraConverter.setAutoYoko(autoYoko, autoYokoNum1, autoYokoNum3, autoYokoEQ1);
        //文字出力設定
        int dakutenType = 0; try { dakutenType = Integer.parseInt(props.getProperty("DakutenType")); } catch (Exception e) {}
        boolean printIvsBMP = "1".equals(props.getProperty("IvsBMP"));
        boolean printIvsSSP = "1".equals(props.getProperty("IvsSSP"));

        aozoraConverter.setCharOutput(dakutenType, printIvsBMP, printIvsSSP);
        //全角スペースの禁則
        aozoraConverter.setSpaceHyphenation(spaceHyp);
        //コメント
        aozoraConverter.setCommentPrint(commentPrint, commentConvert);

        aozoraConverter.setRemoveEmptyLine(removeEmptyLine, maxEmptyLine);

        //強制改ページ
        aozoraConverter.setForcePageBreak(forcePageBreakSize, forcePageBreakEmpty, forcePageBreakEmptySize, forcePageBreakChapter, forcePageBreakChapterSize);
        //目次設定
        aozoraConverter.setChapterLevel(maxLength, chapterExclude, chapterUseNextLine, chapterSection,
                chapterH, chapterH1, chapterH2, chapterH3, sameLineChapter,
                chapterName,
                chapterNumOnly, chapterNumTitle, chapterNumParen, chapterNumParenTitle,
                chapterPattern);


        ////////////////////////////////
        //各ファイルを変換処理
        ////////////////////////////////
        //     for (String fileName : fileNames) {
        LogAppender.println("--------");
        //File srcFile = new File(fileName);
        if(srcFile == null || !srcFile.isFile()) {
            LogAppender.error("file not exist. " + srcFile.getAbsolutePath());
            //    continue;
            return;
        }
        String ext = srcFile.getName();
        ext = ext.substring(ext.lastIndexOf('.') + 1).toLowerCase();
        int coverImageIndex = -1;
        if(coverFileName != null) {
            if("0".equals(coverFileName)) {
                coverImageIndex = 0;
                coverFileName = "";
            } else if("1".equals(coverFileName)) {
                coverFileName = AozoraEpub3.getSameCoverFileName(srcFile); //入力ファイルと同じ名前+.jpg/.png
            }
        }
        //zipならzip内のテキストを検索
        int txtCount = 1;
        boolean imageOnly = false;
        boolean isFile = "txt".equals(ext);
        if("zip".equals(ext) || "txtz".equals(ext)) {
            try {
                txtCount = AozoraEpub3.countZipText(srcFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if(txtCount == 0) {
                txtCount = 1;
                imageOnly = true;
            }
        } else if("rar".equals(ext)) {
            try {
                txtCount = AozoraEpub3.countRarText(srcFile);
            } catch (IOException | RarException e) {
                e.printStackTrace();
            }
            if(txtCount == 0) {
                txtCount = 1;
                imageOnly = true;
            }
        } else if("cbz".equals(ext)) {
            imageOnly = true;
        }
        for(int txtIdx = 0; txtIdx < txtCount; txtIdx++) {
            ImageInfoReader imageInfoReader = new ImageInfoReader(isFile, srcFile);
            BookInfo bookInfo = null;
            //文字コード判別
            String encauto;

            try {
                encauto=AozoraEpub3.getTextCharset(srcFile, ext, imageInfoReader, txtIdx);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (RarException e) {
                throw new RuntimeException(e);
            }
            if (Objects.equals(encauto, "SHIFT_JIS"))encauto="MS932";
            if (encType.equals("AUTO")) encType =encauto;
            if(!imageOnly) {
                bookInfo = AozoraEpub3.getBookInfo(srcFile, ext, txtIdx, imageInfoReader, aozoraConverter, encType, BookInfo.TitleType.indexOf(titleIndex), false);
                bookInfo.vertical = vertical;
                bookInfo.insertTocPage = tocPage;
                bookInfo.setTocVertical(tocVertical);
                bookInfo.insertTitleToc = insertTitleToc;
                aozoraConverter.vertical = vertical;
                //表題ページ
                bookInfo.titlePageType = titlePage;
            }
            //表題の見出しが非表示で行が追加されていたら削除
            if(!bookInfo.insertTitleToc && bookInfo.titleLine >= 0) {
                bookInfo.removeChapterLineInfo(bookInfo.titleLine);
            }
            Epub3Writer writer = epub3Writer;
            if (!isFile) {
                if ("rar".equals(ext)) {
                    try {
                        imageInfoReader.loadRarImageInfos(srcFile, imageOnly);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    } catch (RarException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    try {
                        imageInfoReader.loadZipImageInfos(srcFile, imageOnly);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                if (imageOnly) {
                    LogAppender.println("画像のみのePubファイルを生成します");
                    //画像出力用のBookInfo生成
                    bookInfo = new BookInfo(srcFile);
                    bookInfo.imageOnly = true;
                    //Writerを画像出力用派生クラスに入れ替え
                    writer = epub3ImageWriter;

                    if (imageInfoReader.countImageFileInfos() == 0) {
                        LogAppender.error("画像がありませんでした");
                        return;
                    }
                    //名前順で並び替え
                    imageInfoReader.sortImageFileNames();
                }
            }

            //表題の見出しが非表示で行が追加されていたら削除
            if (!Objects.requireNonNull(bookInfo).insertTitleToc && bookInfo.titleLine >= 0) {
                bookInfo.removeChapterLineInfo(bookInfo.titleLine);
            }

            //先頭からの場合で指定行数以降なら表紙無し
            if ("".equals(coverFileName)) {
                try {
                    int maxCoverLine = Integer.parseInt(props.getProperty("MaxCoverLine"));
                    if (maxCoverLine > 0 && bookInfo.firstImageLineNum >= maxCoverLine) {
                        coverImageIndex = -1;
                        coverFileName = null;
                    }
                } catch (Exception e) {}
            }

            //表紙設定
            bookInfo.insertCoverPageToc = coverPageToc;
            bookInfo.insertCoverPage = coverPage;
            bookInfo.coverImageIndex = coverImageIndex;
            if (coverFileName != null && !coverFileName.startsWith("http")) {
                File coverFile = new File(coverFileName);
                if (!coverFile.exists()) {
                    coverFileName = srcFile.getParent()+"/"+coverFileName;
                    if (!new File(coverFileName).exists()) {
                        coverFileName = null;
                        LogAppender.println("[WARN] 表紙画像ファイルが見つかりません : "+coverFile.getAbsolutePath());
                    }
                }
            }
            bookInfo.coverFileName = coverFileName;

            String[] titleCreator = BookInfo.getFileTitleCreator(srcFile.getName());
            if (useFileName) {
                if (titleCreator[0] != null && !titleCreator[0].trim().isEmpty())
                    bookInfo.title = titleCreator[0];
                if (titleCreator[1] != null && !titleCreator[1].trim().isEmpty())
                    bookInfo.creator = titleCreator[1];
            } else {
//テキストから取得できていない場合
                if (bookInfo.title == null || bookInfo.title.isEmpty())
                    bookInfo.title = titleCreator[0] == null ? "" : titleCreator[0];
                if (bookInfo.creator == null || bookInfo.creator.isEmpty())
                    bookInfo.creator = titleCreator[1] == null ? "" : titleCreator[1];
            }

            outFile = getOutFile(srcFile, dstPath, bookInfo, autoFileName, outExt);
            AozoraEpub3.convertFile(
                    srcFile, ext, outFile,
                    aozoraConverter, writer,
                    encType, bookInfo, imageInfoReader, txtIdx);
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
        intent.setType("application/epub+zip");
        intent.putExtra(Intent.EXTRA_TITLE, outFile.getName());
        saveFileLauncher.launch(intent);
    }

    // 🔹 内部ストレージのファイルを SAF で保存
    private void saveFileToUri(Uri uri) {

        if (!outFile.exists() || outFile.length() == 0) {
            Toast.makeText(this, "出力ファイルが空のため保存できません", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Files.copy(outFile.toPath(), getContentResolver().openOutputStream(uri));
            Toast.makeText(this, "ファイルを保存しました", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "保存に失敗しました", Toast.LENGTH_SHORT).show();
        }
    }

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown(); // Activity終了時にシャットダウン
    }

    /** Web変換処理 */
    private void convertWeb(List<String> urlList, List<File> shortcutFiles, File dstPath) {
        executorService.submit(() -> {
            try {
                for (int i = 0; i < urlList.size(); i++) {
                    String urlString = urlList.get(i);
                    File srcShortcutFile = (shortcutFiles != null && shortcutFiles.size() > i) ? shortcutFiles.get(i) : null;

                    String ext = urlString.substring(urlString.lastIndexOf('.') + 1).toLowerCase();
                    if (ext.equals("zip") || ext.equals("txtz") || ext.equals("rar")) {
                        String fileName = new File(new URI(urlString).getPath()).getName().replaceAll("[?*&|<>\"\\\\]", "_");
                        File srcFile = new File(dstPath, fileName);

                        postLog("出力先にダウンロードします: " + srcFile.getCanonicalPath());

                        if (!srcFile.getParentFile().exists()) {
                            srcFile.getParentFile().mkdirs();
                        }

                        try (BufferedInputStream bis = new BufferedInputStream(new URL(urlString).openStream(), 8192);
                             BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(srcFile))) {
                            byte[] buffer = new byte[8192];
                            int bytesRead;
                            while ((bytesRead = bis.read(buffer)) != -1) {
                                bos.write(buffer, 0, bytesRead);
                            }
                        }

                        // convertFiles(new File[]{srcFile}, dstPath);
                        continue;
                    }

                    postLog("--------");
                    postLog(urlString + " を読み込みます");

                    webConverter = WebAozoraConverter.createWebAozoraConverter(urlString, MainActivity.this);
                    if (webConverter == null) {
                        postLog(urlString + " は変換できませんでした");
                        continue;
                    }

                    int interval = 500;
                    String Ua = "Chrome";
                    int beforeChapter = 0;
                    float modifiedExpire = 0;
                    boolean WebConvertUpdated = false;
                    boolean WebModifiedOnly = false;
                    boolean WebModifiedTail = false;
                    boolean WebLageImage = false;

                    srcFile = webConverter.convertToAozoraText(urlString, getCachePath(this), interval,
                            modifiedExpire, WebConvertUpdated, WebModifiedOnly, WebModifiedTail,
                            beforeChapter, Ua, WebLageImage);

                    if (srcFile == null) {
                        postLog(urlString + " の変換をスキップまたは失敗しました");
                        continue;
                    }

                    // convertFiles(new File[]{srcFile}, dstPath);
                }
            } catch (Exception e) {
                postLog("エラーが発生しました: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /** UIスレッドでログ出力 */
    private void postLog(String message) {
        runOnUiThread(() -> {
            LogAppender.println(message); // ここが UI に表示するメソッド
        });
    }

    /** キャッシュパスを取得 */
    private static File getCachePath(Context context) {
        return context.getCacheDir();
    }


    public void convertFiles( File[] srcFiles, File dstPath) {
        if (srcFiles == null || srcFiles.length == 0) return;

        convertCanceled = false;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // 画面サイズと画像リサイズ
        int resizeW = 0;
        if (prefs.getBoolean("resizeW_enabled", false)) {
            try {
                resizeW = Integer.parseInt(prefs.getString("resizeW_value", "0"));
            } catch (Exception ignored) {}
        }

        int resizeH = 0;
        if (prefs.getBoolean("resizeH_enabled", false)) {
            try {
                resizeH = Integer.parseInt(prefs.getString("resizeH_value", "0"));
            } catch (Exception ignored) {}
        }

        // 表示サイズ
        int dispW = 0;
        try {
            dispW = Integer.parseInt(prefs.getString("dispW", "0"));
        } catch (Exception ignored) {}

        int dispH = 0;
        try {
            dispH = Integer.parseInt(prefs.getString("dispH", "0"));
        } catch (Exception ignored) {}

        // カバー画像サイズ
        int coverW = 0;
        try {
            coverW = Integer.parseInt(prefs.getString("coverW", "0"));
        } catch (Exception ignored) {}

        int coverH = 0;
        try {
            coverH = Integer.parseInt(prefs.getString("coverH", "0"));
        } catch (Exception ignored) {}

        // 単ページ化サイズと幅
        int singlePageSizeW = 0;
        try {
            singlePageSizeW = Integer.parseInt(prefs.getString("singlePageSizeW", "0"));
        } catch (Exception ignored) {}

        int singlePageSizeH = 0;
        try {
            singlePageSizeH = Integer.parseInt(prefs.getString("singlePageSizeH", "0"));
        } catch (Exception ignored) {}

        int singlePageWidth = 0;
        try {
            singlePageWidth = Integer.parseInt(prefs.getString("singlePageWidth", "0"));
        } catch (Exception ignored) {}

        // 画像スケールの取得
        float imageScale = 0;
        if (prefs.getBoolean("imageScale_enabled", false)) {
            try {
                imageScale = Float.parseFloat(prefs.getString("imageScale_value", "0"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        // 画像の回り込み設定（float設定）
        int imageFloatType = 0; // 0=無効, 1=上, 2=下
        int imageFloatW = 0;
        int imageFloatH = 0;

        if (prefs.getBoolean("imageFloat_enabled", false)) {
            imageFloatType = prefs.getInt("imageFloatType", 0); // 1 or 2 を直接保存する

            try {
                imageFloatW = Integer.parseInt(prefs.getString("imageFloatW", "0"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            try {
                imageFloatH = Integer.parseInt(prefs.getString("imageFloatH", "0"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        // JPEGクオリティの取得（0.0f〜1.0f）
        float jpegQuality = 0.8f;
        try {
            jpegQuality = Integer.parseInt(prefs.getString("jpegQuality", "80")) / 100f;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // ガンマ値の取得
        float gamma = 1.0f;
        if (prefs.getBoolean("gamma_enabled", false)) {
            try {
                gamma = Float.parseFloat(prefs.getString("gamma_value", "1.0"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        // 自動マージン設定（有効な場合のみ取得）
        int autoMarginLimitH = 0;
        int autoMarginLimitV = 0;
        int autoMarginWhiteLevel = 0;
        float autoMarginPadding = 0;
        int autoMarginNombre = 0;
        float autoMarginNombreSize = 0.03f;

        if (prefs.getBoolean("autoMargin_enabled", false)) {
            try {
                autoMarginLimitH = Integer.parseInt(prefs.getString("autoMarginLimitH", "0"));
                autoMarginLimitV = Integer.parseInt(prefs.getString("autoMarginLimitV", "0"));
                autoMarginWhiteLevel = Integer.parseInt(prefs.getString("autoMarginWhiteLevel", "0"));
                autoMarginPadding = Float.parseFloat(prefs.getString("autoMarginPadding", "0"));
                autoMarginNombre = prefs.getInt("autoMarginNombre", 0);
                autoMarginNombreSize = Float.parseFloat(prefs.getString("autoMarginNombreSize", "3")) * 0.01f;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        // 回転角度（選択肢 0=なし, 1=右, 2=左）
        int rotateAngle = 0;
        int rotateIndex = prefs.getInt("rotateImage", 0); // 0, 1, 2
        if (rotateIndex == 1) rotateAngle = 90;
        else if (rotateIndex == 2) rotateAngle = -90;

        // 画像サイズタイプ（0=ASPECT, 1=AUTO）
        int imageSizeType = SectionInfo.IMAGE_SIZE_TYPE_ASPECT;
        if (prefs.getBoolean("imageSizeType_auto", false)) {
            imageSizeType = SectionInfo.IMAGE_SIZE_TYPE_AUTO;
        }

        // フィット・SVG画像のフラグ
        boolean fitImage = prefs.getBoolean("fitImage", false);
        boolean svgImage = prefs.getBoolean("svgImage", false);

        // 最終的な設定値をセット
        this.epub3Writer.setImageParam(
                dispW, dispH, coverW, coverH, resizeW, resizeH, singlePageSizeW, singlePageSizeH, singlePageWidth,
                imageSizeType, fitImage, svgImage, rotateAngle,
                imageScale, imageFloatType, imageFloatW, imageFloatH, jpegQuality, gamma,
                autoMarginLimitH, autoMarginLimitV, autoMarginWhiteLevel, autoMarginPadding,
                autoMarginNombre, autoMarginNombreSize
        );

        this.epub3ImageWriter.setImageParam(
                dispW, dispH, coverW, coverH, resizeW, resizeH, singlePageSizeW, singlePageSizeH, singlePageWidth,
                imageSizeType, fitImage, svgImage, rotateAngle,
                imageScale, imageFloatType, imageFloatW, imageFloatH, jpegQuality, gamma,
                autoMarginLimitH, autoMarginLimitV, autoMarginWhiteLevel, autoMarginPadding,
                autoMarginNombre, autoMarginNombreSize
        );

        // 目次階層化設定（ナビゲーションとNCXのネスト）
        boolean navNest = prefs.getBoolean("toc_nav_nest", false);
        boolean ncxNest = prefs.getBoolean("toc_ncx_nest", false);
        this.epub3Writer.setTocParam(navNest, ncxNest);

        // スタイル設定
        String[] pageMargin = new String[4];
        String pageMarginUnit = prefs.getString("pageMargin_unit", "em"); // "em" or "%"
        for (int i = 0; i < 4; i++) {
            pageMargin[i] = prefs.getString("pageMargin" + i, "0") + pageMarginUnit;
        }

        String[] bodyMargin = new String[4];
        String bodyMarginUnit = prefs.getString("bodyMargin_unit", "em"); // "em" or "%"
        for (int i = 0; i < 4; i++) {
            bodyMargin[i] = prefs.getString("bodyMargin" + i, "0") + bodyMarginUnit;
        }

        // 行間
        float lineHeight = 1.8f;
        try {
            lineHeight = Float.parseFloat(prefs.getString("lineHeight", "1.8"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // フォントサイズ
        int fontSize = 100;
        try {
            fontSize = (int) Float.parseFloat(prefs.getString("fontSize", "100"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // 濁点結合タイプ（0=結合, 1=分離, 2=自動）
        int dakutenType = prefs.getInt("dakuten_type", 0);

        // ゴシック・ボールド設定
        boolean boldUseGothic = prefs.getBoolean("bold_use_gothic", false);
        boolean gothicUseBold = prefs.getBoolean("gothic_use_bold", false);

        // 設定を適用
        this.epub3Writer.setStyles(pageMargin, bodyMargin, lineHeight, fontSize, boldUseGothic, gothicUseBold);

        try {
            // 挿絵なし
            this.aozoraConverter.setNoIllust(prefs.getBoolean("no_illust", false));

            // 栞用ID出力
            this.aozoraConverter.setWithMarkId(prefs.getBoolean("with_mark_id", false));

            // 変換オプション設定（自動横組み）
            boolean autoYoko = prefs.getBoolean("auto_yoko", false);
            boolean autoYokoNum1 = prefs.getBoolean("auto_yoko_num1", false);
            boolean autoYokoNum3 = prefs.getBoolean("auto_yoko_num3", false);
            boolean autoEQ1 = prefs.getBoolean("auto_eq1", false);
            this.aozoraConverter.setAutoYoko(autoYoko, autoYokoNum1, autoYokoNum3, autoEQ1);

            // 文字出力設定
            dakutenType = Integer.parseInt(prefs.getString("dakuten_type", "0"));
            boolean ivsBMP = prefs.getBoolean("ivs_bmp", false);
            boolean ivsSSP = prefs.getBoolean("ivs_ssp", false);
            this.aozoraConverter.setCharOutput(dakutenType, ivsBMP, ivsSSP);

            // 全角スペースの禁則
            int spaceHyphenation = Integer.parseInt(prefs.getString("space_hyphenation", "0"));
            this.aozoraConverter.setSpaceHyphenation(spaceHyphenation);

            // 注記のルビ表示
            boolean chukiRuby1 = prefs.getBoolean("chuki_ruby_1", false);
            boolean chukiRuby2 = prefs.getBoolean("chuki_ruby_2", false);
            this.aozoraConverter.setChukiRuby(chukiRuby1, chukiRuby2);

            // コメント変換
            boolean commentPrint = prefs.getBoolean("comment_print", false);
            boolean commentConvert = prefs.getBoolean("comment_convert", false);
            this.aozoraConverter.setCommentPrint(commentPrint, commentConvert);

            // float 表示
            boolean floatPage = prefs.getBoolean("image_float_page", false);
            boolean floatBlock = prefs.getBoolean("image_float_block", false);
            this.aozoraConverter.setImageFloat(floatPage, floatBlock);

            // 空行除去
            int removeEmptyLine = Integer.parseInt(prefs.getString("remove_empty_line", "0"));
            int maxEmptyLine = Integer.parseInt(prefs.getString("max_empty_line", "0"));
            this.aozoraConverter.setRemoveEmptyLine(removeEmptyLine, maxEmptyLine);

            // 行頭字下げ
            this.aozoraConverter.setForceIndent(prefs.getBoolean("force_indent", false));

            // 強制改ページ
            if (prefs.getBoolean("page_break_enabled", false)) {
                try {
                    int forcePageBreakSize = Integer.parseInt(prefs.getString("page_break_size_kb", "0")) * 1024;
                    int forcePageBreakEmpty = 0;
                    int forcePageBreakEmptySize = 0;
                    int forcePageBreakChapter = 0;
                    int forcePageBreakChapterSize = 0;

                    if (prefs.getBoolean("page_break_empty_enabled", false)) {
                        forcePageBreakEmpty = Integer.parseInt(prefs.getString("page_break_empty_line", "0"));
                        forcePageBreakEmptySize = Integer.parseInt(prefs.getString("page_break_empty_size_kb", "0")) * 1024;
                    }

                    if (prefs.getBoolean("page_break_chapter_enabled", false)) {
                        forcePageBreakChapter = 1;
                        forcePageBreakChapterSize = Integer.parseInt(prefs.getString("page_break_chapter_size_kb", "0")) * 1024;
                    }

                    this.aozoraConverter.setForcePageBreak(
                            forcePageBreakSize,
                            forcePageBreakEmpty,
                            forcePageBreakEmptySize,
                            forcePageBreakChapter,
                            forcePageBreakChapterSize
                    );

                } catch (Exception e) {
                    LogAppender.println("強制改ページパラメータ読み込みエラー");
                }
            }

            // 目次設定
            int maxLength = 64;
            try {
                maxLength = Integer.parseInt(prefs.getString("max_chapter_name_length", "64"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            this.aozoraConverter.setChapterLevel(
                    maxLength,
                    prefs.getBoolean("chapter_exclude", false),
                    prefs.getBoolean("chapter_use_next_line", false),
                    prefs.getBoolean("chapter_section", false),
                    prefs.getBoolean("chapter_h", false),
                    prefs.getBoolean("chapter_h1", false),
                    prefs.getBoolean("chapter_h2", false),
                    prefs.getBoolean("chapter_h3", false),
                    prefs.getBoolean("same_line_chapter", false),
                    prefs.getBoolean("chapter_name", false),
                    prefs.getBoolean("chapter_num_only", false),
                    prefs.getBoolean("chapter_num_title", false),
                    prefs.getBoolean("chapter_num_paren", false),
                    prefs.getBoolean("chapter_num_paren_title", false),
                    prefs.getBoolean("chapter_pattern_enabled", false)
                            ? prefs.getString("chapter_pattern", "").trim()
                            : ""
            );

            ////////////////////////////////////
            // すべてのファイルの変換実行
            ////////////////////////////////////
            this._convertFiles(srcFiles, dstPath);

            if (convertCanceled) {
                jProgressBar.setIndeterminate(false);
                jProgressBar.setProgress(0);
            }

        } catch (Exception e) {
            LogAppender.append("エラーが発生しました : ");
            LogAppender.println(e.getMessage());
            throw new RuntimeException(e);
        } finally {
            System.gc(); // Android では明示的なGCは基本不要ですが、互換用に残してあります
        }
    }
    /** サブディレクトリ再帰用 */
    private void _convertFiles(File[] srcFiles, File dstPath)
    {
        for (File srcFile : srcFiles) {
            if (srcFile.isDirectory()) {
                //サブディレクトリ 再帰
                _convertFiles(Objects.requireNonNull(srcFile.listFiles()), dstPath);
            } else if (srcFile.isFile()) {
                convertFile(srcFile, dstPath);
            }
            //キャンセル
            if (convertCanceled) return;
        }
    }
    private void convertFile(File srcFile, File dstPath) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // 拡張子取得
        String ext = srcFile.getName();
        ext = ext.substring(ext.lastIndexOf('.') + 1).toLowerCase();

        // zip なら zip 内のテキストを検索
        int txtCount = 1;
        boolean imageOnly = false;
        LogAppender.append("------ ");
        switch (ext) {
            case "zip":
            case "txtz":
                try {
                    txtCount = AozoraEpub3.countZipText(srcFile);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                if (txtCount == 0) {
                    txtCount = 1;
                    imageOnly = true;
                }
                break;
            case "rar":
                try {
                    txtCount = AozoraEpub3.countRarText(srcFile);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                if (txtCount == 0) {
                    txtCount = 1;
                    imageOnly = true;
                }
                break;
            case "cbz":
                imageOnly = true;
                break;
            case "txt":
                LogAppender.println();
                break;
        }

        if (this.convertCanceled) {
            LogAppender.println("変換処理を中止しました : " + srcFile.getAbsolutePath());
            return;
        }

        // SharedPreferences から現在のエンコード取得
        String encType = prefs.getString("encoding_type", "UTF-8");

        // キャッシュファイルなら一時的に UTF-8 に変更
        String originalEncType = encType;
        if (this.isCacheFile(srcFile,this)) {
            encType = "UTF-8";
        }

        // エンコードを Converter に設定（必要なら）
        //this.aozoraConverter.setEncoding(encType);

        try {
            for (int i = 0; i < txtCount; i++) {
                convertFile(srcFile, dstPath, ext, i, imageOnly,this);
                if (convertCanceled) return;
            }
        } finally {
            // 元のエンコードに戻す（必要なら）
            //this.aozoraConverter.setEncoding(originalEncType);
        }
    }
    /** 内部用変換関数 Appletの設定を引数に渡す
     * @param srcFile 変換するファイル txt,zip,cbz,(rar,cbr)
     * @param dstPath 出力先パス
     * @param txtIdx Zip内テキストファイルの位置
     */
    private void convertFile(File srcFile, File dstPath, String ext, int txtIdx, boolean imageOnly, Context context) {
        //パラメータ設定
        // 対応している拡張子以外は変換しない
        if (!ext.equals("txt") && !ext.equals("txtz") && !ext.equals("zip") &&
                !ext.equals("cbz") && !ext.equals("rar") &&
                !ext.equals("png") && !ext.equals("jpg") && !ext.equals("jpeg") && !ext.equals("gif")) {
            LogAppender.println("txt, txtz, zip, cbz, rar 以外は変換できません");
            return;
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        // 表紙にする挿絵の位置 -1なら挿絵は使わない
        int coverImageIndex = -1;
        // SVG表紙を使用するか
        boolean coverSvg = false;

        // coverFileName を取得（Preferenceに保存されている文字列として）
        String coverFileName = prefs.getString("cover_image", "");
        if ("先頭の挿絵".equals(coverFileName)) {
            coverFileName = "";
            coverImageIndex = 0;
        } else if ("元ファイルと同名".equals(coverFileName)) {
            coverFileName = AozoraEpub3.getSameCoverFileName(srcFile);
        } else if ("表紙なし".equals(coverFileName)) {
            coverFileName = null;
        } else if ("SVG表紙".equals(coverFileName)) {
            coverSvg = true;
            coverFileName = null;
        }

        // 拡張子チェック（txtかどうか）
        boolean isFile = "txt".equals(ext);
        ImageInfoReader imageInfoReader = new ImageInfoReader(isFile, srcFile);

        // zip内の画像読み込み
        try {
            if (!isFile) {
                if ("rar".equals(ext)) {
                    imageInfoReader.loadRarImageInfos(srcFile, imageOnly);
                } else {
                    imageInfoReader.loadZipImageInfos(srcFile, imageOnly);
                }
            }
        } catch (Exception e) {
            LogAppender.error(e.getMessage());
            throw new RuntimeException(e);
        }

        // 文字コード判別
        String encauto = "";
        // エンコード設定を一時退避
        String encType = prefs.getString("encoding_type", "UTF-8");

        try {
            encauto = AozoraEpub3.getTextCharset(srcFile, ext, imageInfoReader, txtIdx);
            if ("SHIFT_JIS".equals(encauto)) encauto = "MS932";
        } catch (IOException | RarException e1) {
            e1.printStackTrace();
        }

        // 自動判別で取得された文字コード
        if (encauto == null) encauto = "UTF-8";

        // Preferenceで「AUTO」が指定されている場合
        if ("AUTO".equals(prefs.getString("encoding_type", "AUTO"))) {
            encType = encauto;
        }

        // BookInfo取得
        BookInfo bookInfo = null;
        try {
            if (!imageOnly) {
                // タイトル形式 (Spinnerなどでintで保存)
                int titleIndex = prefs.getInt("title_type_index", 0);
                // 初公開チェックボックス（Booleanで保存）
                boolean isPubFirst = prefs.getBoolean("publish_first", false);

                bookInfo = AozoraEpub3.getBookInfo(
                        srcFile, ext, txtIdx, imageInfoReader, this.aozoraConverter,
                        encType,
                        BookInfo.TitleType.indexOf(titleIndex),
                        isPubFirst
                );
            }
        } catch (Exception e) {
            LogAppender.error("ファイルが読み込めませんでした : "+srcFile.getPath());
            return;
        }

        if (convertCanceled) {
            LogAppender.println("変換処理を中止しました : "+srcFile.getAbsolutePath());
            return;
        }

        Epub3Writer writer = this.epub3Writer;

        try {
            if (!isFile) {
                if (imageOnly) {
                    LogAppender.println("画像のみのePubファイルを生成します");

                    bookInfo = new BookInfo(srcFile);
                    bookInfo.imageOnly = true;

                    writer = this.epub3ImageWriter;

                    if (imageInfoReader.countImageFileInfos() == 0) {
                        LogAppender.error("画像がありませんでした");
                        return;
                    }

                    imageInfoReader.sortImageFileNames();

                    // プログレスバーの最大値設定など
                    int maxProgress = imageInfoReader.countImageFileInfos() * 11;
                    jProgressBar.setMax(maxProgress);
                    jProgressBar.setProgress(0);
                    jProgressBar.setIndeterminate(false);
                } else {
                    if (imageInfoReader.countImageFileNames() == 0) {
                        coverImageIndex = -1;
                    }

                    imageInfoReader.addNoNameImageFileName();
                }
            }
        } catch (Exception e) {
            LogAppender.error(e.getMessage());
            throw new RuntimeException(e);
        }

        if (bookInfo == null) {
            LogAppender.error("書籍の情報が取得できませんでした");
            return;
        }

        // プログレスバー設定
        if (bookInfo.totalLineNum > 0) {
            int maxProgress;
            if (isFile) {
                maxProgress = bookInfo.totalLineNum / 10 + imageInfoReader.countImageFileNames() * 10;
            } else {
                maxProgress = bookInfo.totalLineNum / 10 + imageInfoReader.countImageFileInfos() * 10;
            }
            jProgressBar.setMax(maxProgress);
            jProgressBar.setProgress(0);
            jProgressBar.setIndeterminate(false);
        }

        // 表紙・目次ページの出力設定
        bookInfo.insertCoverPage = prefs.getBoolean("insert_cover_page", true);
        bookInfo.insertTocPage = prefs.getBoolean("insert_toc_page", true);
        bookInfo.insertCoverPageToc = prefs.getBoolean("insert_coverpage_toc", false);
        bookInfo.insertTitleToc = prefs.getBoolean("insert_title_toc", true);

        // 表題の見出しが非表示で行が追加されていたら削除
        if (!bookInfo.insertTitleToc && bookInfo.titleLine >= 0) {
            bookInfo.removeChapterLineInfo(bookInfo.titleLine);
        }

        // 目次縦書き設定
        bookInfo.setTocVertical(prefs.getBoolean("toc_vertical", true));

        // 縦書き or 横書き
        bookInfo.vertical = prefs.getBoolean("text_vertical", true);
        aozoraConverter.vertical = bookInfo.vertical;

        // 言語設定（EditTextPreferenceなどから取得）
        bookInfo.language = prefs.getString("language", "ja").trim();

        // 表題ページの表示形式
        boolean showTitlePage = prefs.getBoolean("show_title_page", true);
        if (!showTitlePage) {
            bookInfo.titlePageType = BookInfo.TITLE_NONE;
        } else {
            String titleType = prefs.getString("title_page_type", "normal"); // "normal", "middle", "horizontal"
            switch (titleType) {
                case "middle":
                    bookInfo.titlePageType = BookInfo.TITLE_MIDDLE;
                    break;
                case "horizontal":
                    bookInfo.titlePageType = BookInfo.TITLE_HORIZONTAL;
                    break;
                case "normal":
                default:
                    bookInfo.titlePageType = BookInfo.TITLE_NORMAL;
                    break;
            }
        }

        // 先頭画像による自動表紙判定
        if ("".equals(coverFileName) && !imageOnly) {
            try {
                int maxCoverLine = Integer.parseInt(prefs.getString("max_cover_line", "20"));
                if (maxCoverLine > 0 && (bookInfo.firstImageLineNum == -1 || bookInfo.firstImageLineNum >= maxCoverLine)) {
                    coverImageIndex = -1;
                    coverFileName = null;
                } else {
                    coverImageIndex = bookInfo.firstImageIdx;
                }
            } catch (Exception e) {
                LogAppender.error("max_cover_lineの取得に失敗"+e.getMessage());
                //Log.w("Cover", "max_cover_lineの取得に失敗", e);
            }
        }

        // 表紙情報の設定
        bookInfo.coverFileName = coverFileName;
        bookInfo.coverImageIndex = coverImageIndex;
        bookInfo.svgCoverImage = coverSvg;

        // ファイル名からタイトルと著者名を取得
        String[] titleCreator = BookInfo.getFileTitleCreator(srcFile.getName());
        boolean useFileName = prefs.getBoolean("use_file_name", true); // jCheckUseFileName

        if (useFileName) {
            // ファイル名優先
            bookInfo.title = titleCreator[0] != null ? titleCreator[0] : "";
            bookInfo.creator = titleCreator[1] != null ? titleCreator[1] : "";
        } else {
            // テキストから取得できなければファイル名を使う
            if (bookInfo.title == null || bookInfo.title.isEmpty()) {
                bookInfo.title = titleCreator[0] != null ? titleCreator[0] : "";
            }
            if (bookInfo.creator == null || bookInfo.creator.isEmpty()) {
                bookInfo.creator = titleCreator[1] != null ? titleCreator[1] : "";
            }
        }

        // 変換キャンセルされていれば終了
        if (convertCanceled) {
            LogAppender.println("変換処理を中止しました : " + srcFile.getAbsolutePath());
            return;
        }

        // 過去の変換設定を適用
        BookInfoHistory history = getBookInfoHistory(bookInfo);
        if (history != null) {
            if (bookInfo.title.isEmpty()) bookInfo.title = history.title;
            bookInfo.titleAs = history.titleAs;
            if (bookInfo.creator.isEmpty()) bookInfo.creator = history.creator;
            bookInfo.creatorAs = history.creatorAs;
            if (bookInfo.publisher == null) bookInfo.publisher = history.publisher;

            boolean useCoverHistory = prefs.getBoolean("use_cover_history", false);
            boolean showConfirmDialog = prefs.getBoolean("show_confirm_dialog", true);

            if (useCoverHistory) {
                bookInfo.coverEditInfo = history.coverEditInfo;
                bookInfo.coverFileName = history.coverFileName;
                bookInfo.coverExt = history.coverExt;
                bookInfo.coverImageIndex = history.coverImageIndex;

                // 確認ダイアログを表示しない設定なら自動で画像を生成
                /* 変換前確認
                if (!showConfirmDialog && bookInfo.coverEditInfo != null) {
                    try {
                        // ここでは ConfirmDialog 相当のロジックに合わせて置換が必要
                        CoverImageProcessor imageProcessor = new CoverImageProcessor(); // 仮のクラス名
                        imageProcessor.setBookInfo(bookInfo);

                        if (bookInfo.coverImageIndex >= 0 &&
                                bookInfo.coverImageIndex < imageInfoReader.countImageFileNames()) {
                            bookInfo.coverImage = imageInfoReader.getImage(bookInfo.coverImageIndex);
                        } else if (bookInfo.coverImage == null && bookInfo.coverFileName != null) {
                            bookInfo.loadCoverImage(bookInfo.coverFileName);
                        }

                        // 画像をリサイズして加工（coverW, coverHは事前に定義）
                        bookInfo.coverImage = imageProcessor.getModifiedImage(coverW, coverH);

                    } catch (Exception e) {
                        throw new RuntimeException("カバー画像の加工に失敗", e);
                    }
                }
                */
            }
            // 出力拡張子（例: ".epub" or ".mobi"）
            String outExt = prefs.getString("output_extension", ".epub").trim();

            // 確認ダイアログ表示するか
            showConfirmDialog = prefs.getBoolean("show_confirm_dialog", true);

            if (showConfirmDialog) {
                /*ダイアログ確認

                // 表題と著者の初期値
                String title = (bookInfo.title != null) ? bookInfo.title : "";
                String creator = (bookInfo.creator != null) ? bookInfo.creator : "";

                // 章設定
                boolean chapterSection = prefs.getBoolean("chapter_section", false);
                boolean chapterH = prefs.getBoolean("chapter_h", false);
                boolean chapterH1 = prefs.getBoolean("chapter_h1", false);
                boolean chapterH2 = prefs.getBoolean("chapter_h2", false);
                boolean chapterH3 = prefs.getBoolean("chapter_h3", false);
                boolean chapterName = prefs.getBoolean("chapter_name", false);
                boolean chapterNumbering = prefs.getBoolean("chapter_numbering", false);
                boolean chapterPattern = prefs.getBoolean("chapter_pattern", false);

                // 表題スタイルや出版社名先頭表示
                int titleStyleIndex = prefs.getInt("title_style_index", 0);
                boolean pubFirst = prefs.getBoolean("publish_first", false);

                // Android用の確認ダイアログフラグメントに変換
                ConfirmDialogFragment dialog = ConfirmDialogFragment.newInstance(
                        srcFile,
                        (dstPath != null ? dstPath.getAbsolutePath() : srcFile.getParent()),
                        title,
                        creator,
                        titleStyleIndex,
                        pubFirst,
                        bookInfo,
                        imageInfoReader,
                        coverW,
                        coverH,
                        chapterSection, chapterH, chapterH1, chapterH2, chapterH3, chapterName, chapterNumbering, chapterPattern
                );

                dialog.setOnConfirmResultListener(new ConfirmDialogFragment.OnConfirmResultListener() {
                    @Override
                    public void onConfirmed(BookInfo updatedInfo, @Nullable Bitmap coverBitmap, boolean skipped, boolean canceled, boolean rememberConfirm) {
                        if (canceled) {
                            convertCanceled = true;
                            LogAppender.println("変換処理を中止しました : " + srcFile.getAbsolutePath());
                            return;
                        }
                        if (skipped) {
                            setBookInfoHistory(updatedInfo);
                            LogAppender.println("変換をスキップしました : " + srcFile.getAbsolutePath());
                            return;
                        }

                        // 確認不要にする設定を保存
                        prefs.edit().putBoolean("show_confirm_dialog", rememberConfirm).apply();

                        // 情報を反映
                        bookInfo.title = updatedInfo.title;
                        bookInfo.creator = updatedInfo.creator;
                        bookInfo.titleAs = updatedInfo.titleAs;
                        bookInfo.creatorAs = updatedInfo.creatorAs;
                        bookInfo.publisher = updatedInfo.publisher;

                        if (bookInfo.creator.isEmpty()) {
                            bookInfo.creatorLine = -1;
                        }

                        // 表紙画像（Bitmap → BufferedImage 変換が必要な場合も）
                        if (coverBitmap != null) {
                            bookInfo.coverImage = ImageUtil.convertBitmapToBufferedImage(coverBitmap); // ヘルパーメソッドで変換
                            if (updatedInfo.coverImageIndex == -1) {
                                bookInfo.coverImageIndex = -1;
                            }
                        } else {
                            bookInfo.coverImage = null;
                        }

                        setBookInfoHistory(bookInfo);
                    }
                });

                dialog.show(((AppCompatActivity) context).getSupportFragmentManager(), "confirm_dialog");
                */

            } else {
                // 表題の見出しを削除（タイトルTOCが不要な場合）
                if (!bookInfo.insertTitleToc && bookInfo.titleLine >= 0) {
                    bookInfo.removeChapterLineInfo(bookInfo.titleLine);
                }
            }
            // 設定の読み込み
            boolean autoFileName = prefs.getBoolean("auto_file_name", true);
            boolean overWrite = prefs.getBoolean("overwrite_output", false);

// 出力ファイル
            File outFile;
            File outFileOrg = null;

            outFile = AozoraEpub3.getOutFile(srcFile, dstPath, bookInfo, autoFileName, outExt);

            if (!overWrite && outFile.exists()) {
                LogAppender.println("変換中止: " + srcFile.getAbsolutePath());
                LogAppender.println("ファイルが存在します: " + outFile.getAbsolutePath());
                return;
            }
            // 変換実行
            AozoraEpub3.convertFile(
                    srcFile, ext, outFile,
                    aozoraConverter,
                    writer,
                    encType,
                    bookInfo, imageInfoReader, txtIdx
            );

// 設定を戻す（UIがないので必要なければ省略）
            imageInfoReader = null;
            bookInfo.coverImage = null;

// 変換中止された場合
            if (convertCanceled) {
                LogAppender.println("変換処理を中止しました : " + srcFile.getAbsolutePath());
                return;
            }
        }

    }

    private boolean isCacheFile(File file, Context context) {
        try {
            File cacheDir = context.getCacheDir(); // Android のキャッシュパス
            return file.getCanonicalPath().startsWith(cacheDir.getCanonicalPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    ////////////////////////////////////////////////////////////////
    //変換履歴
    ////////////////////////////////////////////////////////////////
    /** 変換履歴格納用 最大255件 */
    LinkedHashMap<String, BookInfoHistory> mapBookInfoHistory = new LinkedHashMap<String, BookInfoHistory>(){
        @Serial
        private static final long serialVersionUID = 1L;
        @SuppressWarnings("rawtypes")
        protected boolean removeEldestEntry(Map.Entry eldest) { return size() > 256; }
    };
    //以前の変換情報取得
    BookInfoHistory getBookInfoHistory(BookInfo bookInfo)
    {
        String key = bookInfo.srcFile.getAbsolutePath();
        if (bookInfo.textEntryName != null) key += "/"+bookInfo.textEntryName;
        return mapBookInfoHistory.get(key);
    }

    void setBookInfoHistory(BookInfo bookInfo)
    {
        String key = bookInfo.srcFile.getAbsolutePath();
        if (bookInfo.textEntryName != null) key += "/"+bookInfo.textEntryName;
        mapBookInfoHistory.put(key, new BookInfoHistory(bookInfo));
    }
}
