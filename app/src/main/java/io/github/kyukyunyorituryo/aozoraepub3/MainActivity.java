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
import android.view.View;
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
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import com.github.junrar.exception.RarException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;


import io.github.kyukyunyorituryo.aozoraepub3.converter.AozoraEpub3Converter;
import io.github.kyukyunyorituryo.aozoraepub3.image.ImageInfoReader;
import io.github.kyukyunyorituryo.aozoraepub3.info.BookInfo;
import io.github.kyukyunyorituryo.aozoraepub3.info.SectionInfo;
import io.github.kyukyunyorituryo.aozoraepub3.util.LogAppender;
import io.github.kyukyunyorituryo.aozoraepub3.writer.Epub3ImageWriter;
import io.github.kyukyunyorituryo.aozoraepub3.writer.Epub3Writer;

public class MainActivity extends AppCompatActivity {
    private File srcFile;
    private Properties props;
    private File outFile;
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
        LogAppender.println("アプリ起動");
        LogAppender.info(42, "情報ログ", "詳細情報");
        LogAppender.warn(100, "警告ログ");
        LogAppender.error(200, "エラーログ", "エラー詳細");

        // 設定値を取得する例
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String selectedTitleType = prefs.getString("TitleType","0");
        boolean autoFileName = prefs.getBoolean("AutoFileName", true);
        String dstPath = prefs.getString("DstPath", "C:\\Users\\Owner\\Downloads");
        int fontSize = prefs.getInt("FontSize", 100);

        LogAppender.println("設定値の取得");
        LogAppender.println(selectedTitleType);
        LogAppender.println(String.valueOf(autoFileName));
        LogAppender.println(dstPath);
        LogAppender.println(String.valueOf(fontSize));

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
        buttonSetting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(intent);
            }
        });
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

            File outFile = getOutFile(srcFile, dstPath, bookInfo, autoFileName, outExt);
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

}
