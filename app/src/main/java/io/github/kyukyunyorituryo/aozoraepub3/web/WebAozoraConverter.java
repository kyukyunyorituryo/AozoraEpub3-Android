package com.github.hmdev.web;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.io.StringWriter;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import com.github.hmdev.util.CharUtils;
import com.github.hmdev.util.LogAppender;
import com.github.hmdev.web.ExtractInfo.ExtractId;

/** HTMLを青空txtに変換 */
public class WebAozoraConverter
{
	final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

	/** Singletonインスタンス格納 keyはFQDN */
	static HashMap<String, WebAozoraConverter> converters = new HashMap<String, WebAozoraConverter>();

	//設定ファイルから読み込むパラメータ
	/** リストページ抽出対象 HashMap<String key, String[]{cssQuery1, cssQuery2}> キーとJsoupのcssQuery(or配列) */
	HashMap<ExtractId, ExtractInfo[]> queryMap;

	/** 出力文字列置換情報 */
	HashMap<ExtractId, Vector<String[]>> replaceMap;

	/** テキスト出力先パス 末尾は/ */
	String dstPath;

	/** DnDされたページのURL文字列 */
	String urlString = null;

	/** http?://fqdn/ の文字列 */
	String baseUri;

	/** 変換中のHTMLファイルのあるパス 末尾は/ */
	String pageBaseUri;

	////////////////////////////////
	//変換設定
	/** 取得間隔 ミリ秒 */
	int interval = 500;
    /** ユーザーエージェント */
    String UserAgent ="";
    /** ウェブ画像の大きい画像を取得 */
    boolean webLageImage =false;

	/** 未更新時は変換スキップ */
	boolean convertUpdated = false;

	/** 追加更新分のみ出力する */
	boolean convertModifiedOnly = false;
	/** 最新話から連続した追加更新分のみ出力 */
	boolean convertModifiedTail = false;
	/** 更新分に追加で変換する話数 */
	int beforeChapter = 0;
	/** この時間前までに取得された追加更新話を変換する */
	float modifiedExpire = 24;

	////////////////////////////////
	//キャンセルリクエストされたらtrue
	boolean canceled = false;
	//更新有りフラグ
	boolean updated = false;

	////////////////////////////////////////////////////////////////
	/** fqdnに対応したインスタンスを生成してキャッシュして変換実行 */
	public static WebAozoraConverter createWebAozoraConverter(String urlString, File configPath) throws IOException
	{
		urlString = urlString.trim();
		String baseUri = urlString.substring(0, urlString.indexOf('/', urlString.indexOf("//")+2));
		String fqdn = baseUri.substring(baseUri.indexOf("//")+2);
		WebAozoraConverter converter = converters.get(fqdn);
		if (converter == null) {
			converter = new WebAozoraConverter(fqdn, configPath);
			if (!converter.isValid()) {
				LogAppender.println("サイトの定義がありません: "+configPath.getName()+"/"+fqdn);
				return null;
			}
			converters.put(fqdn, converter);
		}
		return converter;
		//return converter._convertToAozoraText(urlString, baseUri, fqdn, cachePath);
	}

	////////////////////////////////////////////////////////////////
	/** fqdnに対応したパラメータ取得
	 * @throws IOException */
	WebAozoraConverter(String fqdn, File configPath) throws IOException
	{
		if (configPath.isDirectory()) {
			for (File file : Objects.requireNonNull(configPath.listFiles())) {
				if (file.isDirectory() && file.getName().equals(fqdn)) {

					//抽出情報
					File extractInfoFile = new File(configPath.getAbsolutePath()+"/"+fqdn+"/extract.txt");
					if (!extractInfoFile.isFile()) return;
					this.queryMap = new HashMap<ExtractId, ExtractInfo[]>();
					String line;
					BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(extractInfoFile), StandardCharsets.UTF_8));
					try {
						while ((line = br.readLine()) != null) {
							if (line.isEmpty() || line.charAt(0) == '#') continue;
							String[] values = line.split("\t", -1);
							if (values.length > 1) {
								ExtractId extractId = ExtractId.valueOf(values[0]);
								String[] queryStrings = values[1].split(",");
								Pattern pattern = values.length > 2 ? Pattern.compile(values[2]) : null; //ExtractInfoが複数でも同じ値を設定
								String replaceValue = values.length > 3 ? values[3] : null; //ExtractInfoが複数でも同じ値を設定
								ExtractInfo[] extractInfos = new ExtractInfo[queryStrings.length];
								for (int i=0; i<queryStrings.length; i++) extractInfos[i] = new ExtractInfo(queryStrings[i], pattern, replaceValue);
								this.queryMap.put(extractId, extractInfos);
							}
						}
					} finally{
						br.close();
					}

					//置換情報
					this.replaceMap = new HashMap<ExtractId, Vector<String[]>>();
					File replaceInfoFile = new File(configPath.getAbsolutePath()+"/"+fqdn+"/replace.txt");
					if (replaceInfoFile.isFile()) {
						br = new BufferedReader(new InputStreamReader(new FileInputStream(replaceInfoFile), StandardCharsets.UTF_8));
						try {
							while ((line = br.readLine()) != null) {
								if (line.isEmpty() || line.charAt(0) == '#') continue;
								String[] values = line.split("\t");
								if (values.length > 1) {
									ExtractId extractId = ExtractId.valueOf(values[0]);
                                    Vector<String[]> vecReplace = this.replaceMap.computeIfAbsent(extractId, k -> new Vector<String[]>());
                                    vecReplace.add(new String[]{values[1], values.length==2?"":values[2]});
								}
							}
						} finally{
							br.close();
						}
					}
					return;
				}
			}
		}
	}

	////////////////////////////////////////////////////////////////
	private boolean isValid()
	{
		return this.queryMap != null;
	}

	public void canceled()
	{
		this.canceled = true;
	}
	public boolean isCanceled()
	{
		return this.canceled;
	}
	public boolean isUpdated()
	{
		return this.updated;
	}

	////////////////////////////////////////////////////////////////
	/**
     * 変換実行
     *
     * @param urlString
     * @param cachePath
     * @param interval
     * @param modifiedExpire      この時間以内のキャッシュを更新分として扱う
     * @param convertUpdated      更新時のみ出力
     * @param convertModifiedOnly 追加更新分のみ変換
     * @param convertModifiedTail 最新話から連続したもののみ変換
     * @param beforeChapter       指定話数のみ変換 0は指定無し
     * @param UserAgent ユーザー絵ジェント
     * @param webLageImage ウェブ画像の大きい画像を取得
     * @return 変換スキップやキャンセルならnullを返す
     */
	public File convertToAozoraText(String urlString, File cachePath, int interval, float modifiedExpire,
                                    boolean convertUpdated, boolean convertModifiedOnly, boolean convertModifiedTail, int beforeChapter, String UserAgent, boolean webLageImage) throws IOException
	{
		this.canceled = false;
		//日付一覧が取得できない場合は常に更新
		this.updated = true;

		this.interval = Math.max(500, interval);
        this.UserAgent=UserAgent;
        this.webLageImage = webLageImage;
		this.modifiedExpire = Math.max(0, modifiedExpire);
		this.convertUpdated = convertUpdated;
		this.convertModifiedOnly = convertModifiedOnly;
		this.convertModifiedTail = convertModifiedTail;
		this.beforeChapter = beforeChapter;

		//末尾の / をリダイレクトで取得
		urlString = urlString.trim();
		if (!urlString.endsWith("/") && !urlString.endsWith(".html") && !urlString.endsWith(".htm") && !urlString.contains("?")) {
			HttpURLConnection connection = null;
			try {
				connection = (HttpURLConnection) new URI(urlString+"/").toURL().openConnection();
				if (connection.getResponseCode() == 200) {
					urlString += "/";
					LogAppender.println("URL修正 : "+urlString);
				}
			} catch (Exception e) {
			} finally {
				if (connection != null) connection.disconnect();
			}
		}

		this.urlString = urlString;

		this.baseUri = urlString.substring(0, urlString.indexOf('/', urlString.indexOf("//")+2));
		//String fqdn = baseUri.substring(baseUri.indexOf("//")+2);
		String listBaseUrl = urlString.substring(0, urlString.lastIndexOf('/')+1);
		this.pageBaseUri = listBaseUrl;
		//http://を除外
		String urlFilePath = CharUtils.escapeUrlToFile(urlString.substring(urlString.indexOf("//")+2));
		//http://を除外した文字列で比較
		/*ExtractInfo[] extractInfos = this.queryMap.get(ExtractId.PAGE_REGEX);
		if(extractInfos != null) {
			if (!extractInfos[0].matches(urlString)) {
				LogAppender.println("読み込み可能なURLではありません");
				return null;
			}
		}*/

		String urlParentPath = urlFilePath;
		//kakuyomu用のアドレス修正
		if((urlFilePath.contains("kakuyomu")) || (urlFilePath.contains("novelup"))) {
			urlFilePath +="/";
			LogAppender.println("URL修正");
		}

		boolean isPath = false;
		if (urlFilePath.endsWith("/")) { isPath = true; urlFilePath += "index.html"; }
		else urlParentPath = urlFilePath.substring(0, urlFilePath.lastIndexOf('/')+1);

		//変換結果
		this.dstPath = cachePath.getAbsolutePath()+"/";
		if (isPath) this.dstPath += urlParentPath;
		else this.dstPath += urlFilePath+"_converted/";
		File txtFile = new File(this.dstPath+"converted.txt");
		//表紙画像はtxtと同じ名前で保存 拡張子はpngだが表示はできるのでそのまま
		File coverImageFile = new File(this.dstPath+"converted.png");
		//更新情報格納先
		File updateInfoFile = new File(this.dstPath+"update.txt");

		//フォルダ以外がすでにあったら削除
		File parentFile = txtFile.getParentFile();
		if (parentFile.exists() && !parentFile.isDirectory()) {
			parentFile.delete();
		}
		parentFile.mkdirs();
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(txtFile), StandardCharsets.UTF_8))) {

            //urlStringのファイルをキャッシュ
            File cacheFile = new File(cachePath.getAbsolutePath() + "/" + urlFilePath);
            try {
                LogAppender.append(urlString);
                cacheFile(urlString, cacheFile, null);
                LogAppender.println(" : List Loaded.");
            } catch (Exception e) {
                e.printStackTrace();
                LogAppender.println("一覧ページの取得に失敗しました。 ");
                if (!cacheFile.exists()) return null;

                LogAppender.println("キャッシュファイルを利用します。");
            }

            //パスならlist.txtの情報を元にキャッシュ後に青空txt変換して改ページで繋げて出力
            Document doc = Jsoup.parse(cacheFile, null);

            //ページネーション対応
            Elements toc_index = getExtractElements(doc, this.queryMap.get(ExtractId.INDEX));
            if (toc_index != null) {
                //LogAppender.println("目次がある");
            }
            Elements lastPage = getExtractElements(doc, this.queryMap.get(ExtractId.LAST_PAGE));
            if (lastPage == null) {
                LogAppender.println("ページャーがありません");
            }
            //ページャーのリンクがある場合
            if (lastPage != null) {
                //最終ページ番号
                String links = lastPage.attr("href").split("\\?")[1].replaceAll("[^0-9]", "");
                int lastNum = Integer.parseInt(links);
                //System.out.println(lastNum);
                //末尾の数字以外のページャーURL、base_url
                String pagerBseUrl = lastPage.attr("href").split("=")[0];
                if (!pagerBseUrl.startsWith("http")) {
                    pagerBseUrl = baseUri + pagerBseUrl;
                }
                List<String> pagerUrlList = new ArrayList<>();
                for (int i = 2; i <= lastNum; i++) {
                    String str = pagerBseUrl + "=" + i;
                    pagerUrlList.add(str);
                }
                //System.out.println(Arrays.toString(url_list.toArray()));

                ExtractInfo[] pagerEle = this.queryMap.get(ExtractId.PAGER_MAX);
                String pagerMax = "";
                if (pagerEle != null && pagerEle.length > 0) pagerMax = pagerEle[0].query;
                LogAppender.println("ページャー最大値は" + pagerMax);
                //LogAppender.println(String.valueOf(toc_index));
                //link=n00000/?p=2
                //baseUri=https://ncode.syosetu.com/
                //System.out.println(baseUri+link);/
                //pagerMaxと最終ページの小さい方
                int page = Math.min(pagerUrlList.size(), Integer.parseInt(pagerMax));
                //目次２ページ目から最終ページまでの取得処理ループ
                for (int i = 0; i < page; i++) {
                    String pagerUrl = pagerUrlList.get(i);
                    String pagerurlFilePath = CharUtils.escapeUrlToFile(pagerUrl.substring(pagerUrl.indexOf("//") + 2));
                    //urlStringのファイルをキャッシュ
                    File pagerCacheFile = new File(cachePath.getAbsolutePath() + "/" + pagerurlFilePath);
                    try {
                        LogAppender.append(pagerUrl);
                        try {
                            Thread.sleep(this.interval);
                        } catch (InterruptedException e) {
                        }
                        cacheFile(pagerUrl, pagerCacheFile, null);
                        LogAppender.println(" : List Loaded.");
                    } catch (Exception e) {
                        e.printStackTrace();
                        LogAppender.println("一覧ページの取得に失敗しました。 ");
                        if (!pagerCacheFile.exists()) return null;

                        LogAppender.println("キャッシュファイルを利用します。");
                    }
                    Document pagedoc = Jsoup.parse(pagerCacheFile, null);
                    Elements index = Objects.requireNonNull(getExtractElements(pagedoc, this.queryMap.get(ExtractId.INDEX)).first()).children().clone();
                    //Elements index = pagedoc.getElementsByClass("index_box").first().children().clone();
                    Objects.requireNonNull(toc_index).append(String.valueOf(index));
                }

            }


            //カクヨムのJSON取得してHTMLに変換
            if (urlFilePath.contains("kakuyomu")) {
                String cd = urlString.substring(urlString.lastIndexOf('/') + 1);
                //System.out.println("current=" + cd);
                Element NEXT_DATA = doc.getElementById("__NEXT_DATA__");
                //System.out.println(NEXT_DATA.data());
                String jsonText = NEXT_DATA != null ? NEXT_DATA.data() : null;
                JSONObject json = new JSONObject(Objects.requireNonNull(jsonText));
                JSONObject episode = json.getJSONObject("props").getJSONObject("pageProps").getJSONObject("__APOLLO_STATE__");
                String title = episode.getJSONObject("Work:" + cd).getString("title");
                String introduction = episode.getJSONObject("Work:" + cd).getString("introduction");
                String altauthor = "";
                if (episode.getJSONObject("Work:" + cd).optString("alternateAuthorName", null) != null) {
                    altauthor = episode.getJSONObject("Work:" + cd).getString("alternateAuthorName") + "／";
                }
                String author = altauthor + episode.getJSONObject(episode.getJSONObject("Work:" + cd).getJSONObject("author").getString("__ref")).getString("activityName");
                JSONArray toc = episode.getJSONObject("Work:" + cd).getJSONArray("tableOfContents");
                List<String> page = new ArrayList<String>();
                String[] tocc = new String[toc.length()];
                for (int i = 0; i < toc.length(); i++) {
                    tocc[i] = episode.getJSONObject("Work:" + cd).getJSONArray("tableOfContents").getJSONObject(i).getString("__ref");
                    for (int j = 0; j < episode.getJSONObject(tocc[i]).getJSONArray("episodeUnions").length(); j++) {
                        page.add(episode.getJSONObject(tocc[i]).getJSONArray("episodeUnions").getJSONObject(j).getString("__ref"));
                    }
                }
                Iterator<String> keys = episode.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    if (episode.get(key) instanceof JSONObject) {
                        //フィルタリングします
                        if (!key.startsWith("Episode:")) {
                            keys.remove();
                        }
                    }
                }
                //System.out.println("タイトルは" + title + "イントロは" + introduction + "著者は" + author);

                //System.out.println(episode.length());
                String[][] book = new String[episode.length()][];
                String[] array = page.toArray(new String[0]);
                for (int i = 0; i < episode.length(); i++) {
                    book[i] = new String[3];
                    book[i][0] = episode.getJSONObject(array[i]).getString("id");
                    book[i][1] = episode.getJSONObject(array[i]).getString("title");
                    book[i][2] = episode.getJSONObject(array[i]).getString("publishedAt");
                    ZonedDateTime zdt = ZonedDateTime.parse(book[i][2]);
                    Instant ins1 = zdt.toInstant();
                    Date d = Date.from(ins1);
                    SimpleDateFormat sf = new SimpleDateFormat("yyyy年MM月dd日");
                    book[i][2] = sf.format(d);

                }
                String template = """
                        <h1 id="workTitle"><a href="">$title</a></h1>
                        <span id="workAuthor-activityName"><a href="">$author</a></span>
                        <p id="introduction">$introduction</p>
                        <ol class="widget-toc-items">
                        #set( $i=0)
                        #foreach( $object in $book)
                        <a href="/works/$cd/episodes/$book[$i][0]">
                        <time class="widget-toc-episode-datePublished">$book[$i][2]</time>
                        </a>
                        #set($i=$i + 1)
                        #end
                        </ol>
                        """;
                VelocityContext context = new VelocityContext();
                context.put("title", title);
                context.put("cd", cd);
                context.put("author", author);
                context.put("introduction", introduction);
                context.put("book", book);

                StringWriter sw = new StringWriter();
                Velocity.evaluate(context, sw, "", template);
                //System.out.println(sw);
                doc = Jsoup.parse(sw.toString());
            }

            //表紙画像
            Elements images = getExtractElements(doc, this.queryMap.get(ExtractId.COVER_IMG));
            if (images != null) {
                printImage(null, images.getFirst(), coverImageFile);
            }

            //タイトル
            boolean hasTitle = false;
            String series = getExtractText(doc, this.queryMap.get(ExtractId.SERIES));
            if (series != null) {
                printText(bw, series);
                bw.append('\n');
                hasTitle = true;
            }
            String title = getExtractText(doc, this.queryMap.get(ExtractId.TITLE));
            if (title != null) {
                printText(bw, title);
                bw.append('\n');
                hasTitle = true;
            }
            if (!hasTitle) {
                LogAppender.println("SERIES/TITLE : タイトルがありません");
                return null;
            }

            //著者
            String author = getExtractText(doc, this.queryMap.get(ExtractId.AUTHOR));
            if (author != null) {
                printText(bw, author);
            }
            bw.append('\n');
            //説明
            Element description = getExtractFirstElement(doc, this.queryMap.get(ExtractId.DESCRIPTION));
            if (description != null) {
                bw.append('\n');
                bw.append("［＃区切り線］\n");
                bw.append('\n');
                bw.append("［＃ここから２字下げ］\n");
                bw.append("［＃ここから２字上げ］\n");
                printNode(bw, description, true);
                bw.append('\n');
                bw.append("［＃ここで字上げ終わり］\n");
                bw.append("［＃ここで字下げ終わり］\n");
                bw.append('\n');
                bw.append("［＃区切り線］\n");
                bw.append('\n');
            }

            String contentsUpdate = getExtractText(doc, this.queryMap.get(ExtractId.UPDATE));

            //章名称 変わった場合に出力
            String preChapterTitle = "";

            //各話のURL(フルパス)を格納
            Vector<String> chapterHrefs = new Vector<String>();

            Elements hrefs = getExtractElements(doc, this.queryMap.get(ExtractId.HREF));
            if (hrefs == null && this.queryMap.containsKey(ExtractId.HREF)) {
                LogAppender.println("HREF : 各話のリンク先URLが取得できません");
            }

            Vector<String> subtitles = getExtractStrings(doc, this.queryMap.get(ExtractId.SUBTITLE_LIST), true);
            if (subtitles == null && this.queryMap.containsKey(ExtractId.SUBTITLE_LIST)) {
                LogAppender.println("SUBTITLE_LIST : 各話タイトルが取得できません");
            }

            //更新のない各話のURL(フルパス)を格納
            //nullならキャッシュ更新無しで、空ならすべて更新される
            HashSet<String> noUpdateUrls = null;
            String[] postDateList = null;
            if (hrefs == null) {
                //ページ番号取得
                String pageNumString = getExtractText(doc, this.queryMap.get(ExtractId.PAGE_NUM));
                if (pageNumString == null && this.queryMap.containsKey(ExtractId.PAGE_NUM)) {
                    LogAppender.println("PAGE_NUM : ページ数が取得できません");
                }
                int pageNum = -1;
                try {
                    pageNum = Integer.parseInt(Objects.requireNonNull(pageNumString));
                } catch (Exception e) {
                }
                Element pageUrlElement = getExtractFirstElement(doc, this.queryMap.get(ExtractId.PAGE_URL));
                if (pageUrlElement == null && this.queryMap.containsKey(ExtractId.PAGE_URL)) {
                    LogAppender.println("PAGE_URL : ページ番号用のURLが取得できません");
                }
                if (pageNum > 0 && pageUrlElement != null) {
                    ExtractInfo pageUrlExtractInfo = this.queryMap.get(ExtractId.PAGE_URL)[0];
                    //リンク生成 1～ページ番号まで
                    for (int i = 1; i <= pageNum; i++) {
                        String pageUrl = pageUrlElement.attr("href");
                        pageUrl = pageUrlExtractInfo.replace(pageUrl + "\t" + i);
                        if (pageUrl != null) {
                            if (!pageUrl.startsWith("http")) {
                                if (pageUrl.charAt(0) == '/') pageUrl = baseUri + pageUrl;
                                else pageUrl = listBaseUrl + pageUrl;
                            }
                            chapterHrefs.add(pageUrl);
                        }
                    }
                } else {
                    Elements contentDivs = getExtractElements(doc, this.queryMap.get(ExtractId.CONTENT_ARTICLE));
                    if (contentDivs != null) {
                        //一覧のリンクはないが本文がある場合
                        docToAozoraText(bw, doc, false, null, null);
                    } else {
                        LogAppender.println("一覧のURLが取得できませんでした");
                        return null;
                    }
                }
            } else {
                //更新分のみ取得するようにするためhrefに対応した日付タグの文字列(innerHTML)を取得して保存しておく
                Elements updates = getExtractElements(doc, this.queryMap.get(ExtractId.SUB_UPDATE));
                if (updates == null && this.queryMap.containsKey(ExtractId.SUB_UPDATE)) {
                    LogAppender.println("SUB_UPDATE : 更新確認情報が取得できません");
                }
                if (updates != null) {
                    //更新しないURLのチェック用
                    noUpdateUrls = createNoUpdateUrls(updateInfoFile, urlString, listBaseUrl, contentsUpdate, hrefs, updates);
                }
                //一覧のhrefをすべて取得
                for (Element href : hrefs) {
                    String hrefString = href.attr("href");
                    if (hrefString.isEmpty()) continue;
                    //パターンがあればマッチング
                    ExtractInfo extractInfo = this.queryMap.get(ExtractId.HREF)[0];
                    if (!extractInfo.hasPattern() || extractInfo.matches(hrefString)) {
                        String chapterHref = hrefString;
                        if (!hrefString.startsWith("http")) {
                            if (hrefString.charAt(0) == '/') chapterHref = baseUri + hrefString;
                            else chapterHref = listBaseUrl + hrefString;
                        }
                        chapterHrefs.add(chapterHref);
                    }
                }

                postDateList = getPostDateList(doc, this.queryMap.get(ExtractId.CONTENT_UPDATE_LIST));
                if (postDateList == null && this.queryMap.containsKey(ExtractId.CONTENT_UPDATE_LIST)) {
                    LogAppender.println("CONTENT_UPDATE_LIST : 一覧ページの更新日時情報が取得できません");
                }
            }

            if (!chapterHrefs.isEmpty()) {
                //全話で更新や追加があるかチェック
                updated = false;

                //追加更新対象の期限 これより大きければ追加更新
                long expire = System.currentTimeMillis() - (long) (this.modifiedExpire * 3600000);
                //追加更新分のみ出力時に利用
                HashSet<Integer> modifiedChapterIdx = null;
                //更新されていない最後の話数 0～
                int lastNoModifiedChapterIdx = -1;
                if (this.convertModifiedOnly) {
                    modifiedChapterIdx = new HashSet<Integer>();
                }

                int chapterIdx = 0;
                for (String chapterHref : chapterHrefs) {
                    if (this.canceled) return null;

                    if (chapterHref != null && !chapterHref.isEmpty()) {
                        //画像srcをフルパスにするときに使うページのパス
                        this.pageBaseUri = chapterHref;
                        if (!chapterHref.endsWith("/")) {
                            int idx = chapterHref.indexOf('/', 7);
                            if (idx > -1) this.pageBaseUri = chapterHref.substring(0, idx);
                        }

                        //キャッシュ取得 ロードされたらWait 500ms
                        String chapterPath = CharUtils.escapeUrlToFile(chapterHref.substring(chapterHref.indexOf("//") + 2));
                        File chapterCacheFile = new File(cachePath.getAbsolutePath() + "/" + chapterPath + (chapterPath.endsWith("/") ? "index.html" : ""));
                        //hrefsのときは更新分のみurlsに入っている
                        boolean loaded = false;

                        //更新対象ならtrueに変更
                        boolean reload = noUpdateUrls != null && !noUpdateUrls.contains(chapterHref);
                        //nullでなく更新無しに含まれなければ再読込

                        if (reload || !chapterCacheFile.exists()) {
                            LogAppender.append("[" + (chapterIdx + 1) + "/" + chapterHrefs.size() + "] " + chapterHref);
                            try {
                                try {
                                    Thread.sleep(this.interval);
                                } catch (InterruptedException e) {
                                }
                                cacheFile(chapterHref, chapterCacheFile, urlString);
                                LogAppender.println(" : Loaded.");
                                //ファイルがロードされたら更新有り
                                this.updated = true;
                                loaded = true;
                            } catch (Exception e) {
                                e.printStackTrace();
                                LogAppender.println("htmlファイルが取得できませんでした : " + chapterHref);
                            }
                        }
                        //キャッシュされているファイルが指定時間内なら更新扱い
                        if (!loaded) {
                            if (this.modifiedExpire > 0 && (this.convertModifiedOnly || this.convertUpdated) && chapterCacheFile.lastModified() >= expire) {
                                LogAppender.append("[" + (chapterIdx + 1) + "/" + chapterHrefs.size() + "] " + chapterHref);
                                LogAppender.println(" : Modified.");
                                this.updated = true;
                            }
                        }
                        //更新分のみ出力時のチェック
                        if (this.convertModifiedOnly) {
                            //ファイルの更新日時で比較
                            if (chapterCacheFile.lastModified() >= expire) {
                                Objects.requireNonNull(modifiedChapterIdx).add(chapterIdx);
                            } else {
                                if (this.convertModifiedTail) {
                                    //最新から連続していない話は除外
                                    Objects.requireNonNull(modifiedChapterIdx).clear();
                                }
                                lastNoModifiedChapterIdx = chapterIdx;
                            }
                        }
                    }
                    chapterIdx++;
                }
                //更新が無くて変換もなければ終了
                if (!this.updated) {
                    LogAppender.append("「" + title + "」");
                    LogAppender.println("の更新はありません");
                    if (this.convertUpdated) return null;
                }

                if (this.convertModifiedOnly) {
                    //更新前の話数を追加 昇順で重複もはじく
                    if (this.beforeChapter > 0) {
                        int startIdx = Math.max(0, lastNoModifiedChapterIdx - this.beforeChapter + 1);
                        if (Objects.requireNonNull(modifiedChapterIdx).isEmpty()) {
                            //追加分なし
                            int idx = chapterHrefs.size() - 1;
                            for (int i = 0; i < this.beforeChapter; i++) {
                                modifiedChapterIdx.add(idx--);
                            }
                        } else {
                            //追加分あり
                            for (int i = startIdx; i <= lastNoModifiedChapterIdx; i++) {
                                modifiedChapterIdx.add(i);
                            }
                        }
                    }
                    if (Objects.requireNonNull(modifiedChapterIdx).isEmpty()) {
                        LogAppender.println("追加更新分はありません");
                        this.updated = false;
                        return null;
                    }
                } else {
                    //最新話数指定
                    if (this.beforeChapter > 0) {
                        int idx = chapterHrefs.size() - 1;
                        modifiedChapterIdx = new HashSet<Integer>();
                        for (int i = 0; i < this.beforeChapter; i++) {
                            modifiedChapterIdx.add(idx--);
                        }
                    }
                }

                //変換実行
                chapterIdx = 0;
                for (String chapterHref : chapterHrefs) {
                    if (this.canceled) return null;

                    if (modifiedChapterIdx == null || modifiedChapterIdx.contains(chapterIdx)) {
                        //キャッシュファイル取得
                        String chapterPath = CharUtils.escapeUrlToFile(chapterHref.substring(chapterHref.indexOf("//") + 2));
                        File chapterCacheFile = new File(cachePath.getAbsolutePath() + "/" + chapterPath + (chapterPath.endsWith("/") ? "index.html" : ""));
                        //シリーズタイトルを出力
                        Document chapterDoc = Jsoup.parse(chapterCacheFile, null);
                        String chapterTitle = getExtractText(chapterDoc, this.queryMap.get(ExtractId.CONTENT_CHAPTER));
                        boolean newChapter = false;
                        if (chapterTitle != null && !preChapterTitle.equals(chapterTitle)) {
                            newChapter = true;
                            preChapterTitle = chapterTitle;
                            bw.append("\n［＃改ページ］\n");
                            bw.append("［＃大見出し］");
                            printText(bw, preChapterTitle);
//						bw.append('\n');
                            bw.append("［＃大見出し終わり］\n");
                            bw.append('\n');
                        }
                        //更新日時を一覧から取得
                        String postDate = null;
                        if (postDateList != null && postDateList.length > chapterIdx) {
                            postDate = postDateList[chapterIdx];
                        }
                        String subTitle = null;
                        if (subtitles != null && subtitles.size() > chapterIdx) subTitle = subtitles.get(chapterIdx);

                        docToAozoraText(bw, chapterDoc, newChapter, subTitle, postDate);
                    }
                    chapterIdx++;
                }

                //出力話数を表示
                if (modifiedChapterIdx != null) {
                    StringBuilder buf = new StringBuilder();
                    int preIdx = -1;
                    boolean idxConnected = false;
                    //出力話数生成
                    for (int idx = 0; idx < chapterHrefs.size(); idx++) {
                        if (modifiedChapterIdx.contains(idx)) {
                            if (buf.isEmpty()) buf.append((idx + 1));
                            else {
                                if (preIdx == idx - 1) {
                                    idxConnected = true;
                                } else {
                                    if (idxConnected) buf.append("-").append(preIdx + 1);
                                    idxConnected = false;
                                    buf.append(",").append(idx);
                                }
                            }
                            preIdx = idx;
                        }
                    }
                    if (idxConnected) buf.append("-").append(preIdx + 1);
                    LogAppender.println(buf + "話を変換します");
                }
            }
            //底本にURL追加
            bw.append("\n［＃改ページ］\n");
            bw.append("底本： ");
            bw.append("<a href=\"");
            bw.append(urlString);
            bw.append("\">");
            bw.append(urlString);
            bw.append("</a>");
            bw.append('\n');
            bw.append("変換日時： ");
            bw.append(dateFormat.format(new Date()));
            bw.append('\n');

        }

		this.canceled = false;
		return txtFile;
	}

	/** 更新情報の生成と保存 */
	private HashSet<String> createNoUpdateUrls(File updateInfoFile, String urlString, String listBaseUrl, String contentsUpdate, Elements hrefs, Elements updates) {
		HashMap<String, String> updateStringMap = new HashMap<String, String>();

		if (hrefs == null || updates == null || hrefs.size() != updates.size()) {

			return null;

		} else {
			if (updateInfoFile.exists()) {
				//前回の更新情報を取得して比較
                try (BufferedReader updateBr = new BufferedReader(new InputStreamReader(new FileInputStream(updateInfoFile), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = updateBr.readLine()) != null) {
                        int idx = line.indexOf("\t");
                        if (idx > 0) {
                            updateStringMap.put(line.substring(0, idx), line.substring(idx + 1));
                        }
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
			}
		}

        //ファイルに出力
        try (BufferedWriter updateBw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(updateInfoFile), StandardCharsets.UTF_8))) {
            if (contentsUpdate != null) {
                updateBw.append(urlString);
                updateBw.append('\t');
                updateBw.append(contentsUpdate);
                updateBw.append('\n');
            }
            int i = 0;
            for (Element update : updates) {
                updateBw.append(hrefs.get(i++).attr("href"));
                updateBw.append('\t');
                updateBw.append(update.html().replaceAll("\n", " "));
                updateBw.append('\n');
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        HashSet<String> noUpdateUrls = new HashSet<String>();
		int i = -1;
		for (Element href : hrefs) {
			i++;
			String hrefString = href.attr("href");
			if (hrefString.isEmpty()) continue;
			String updateString = updateStringMap.get(hrefString);
			String html  = updates.get(i).html().replaceAll("\n", " ");
			if (updateString != null && updateString.equals(html)) {
				String chapterHref = hrefString;
				if (!hrefString.startsWith("http")) {
					if (hrefString.charAt(0) == '/') chapterHref = baseUri+hrefString;
					else chapterHref = listBaseUrl+hrefString;
				}
				noUpdateUrls.add(chapterHref);
			}
		}


		return noUpdateUrls;
	}

	/** 一覧から更新日時を取得 */
	private String[] getPostDateList(Document doc, ExtractInfo[] extractInfos)
	{
		if (extractInfos == null) return null;
		for (ExtractInfo extractInfo : extractInfos) {
			Elements elements = doc.select(extractInfo.query);
			if (elements.isEmpty()) continue;
			String[] postDateList = new String[elements.size()];
			for (int i=0; i<postDateList.length; i++) {
				postDateList[i] = extractInfo.replace(elements.get(i).html());
			}
			return postDateList;
		}
		return null;
	}

	/** 各話のHTMLの変換
	 * @param listSubTitle 一覧側で取得したタイトル */
	private void docToAozoraText(BufferedWriter bw, Document doc, boolean newChapter, String listSubTitle, String postDate) throws IOException
	{
		Elements contentDivs = getExtractElements(doc, this.queryMap.get(ExtractId.CONTENT_ARTICLE));
		if (contentDivs == null || contentDivs.isEmpty()) {
			LogAppender.println("CONTENT_ARTICLE : 本文が取得できません");
		} else {
			if (!newChapter) bw.append("\n［＃改ページ］\n");
			String subTitle = getExtractText(doc, this.queryMap.get(ExtractId.CONTENT_SUBTITLE));
			if (subTitle == null) subTitle = listSubTitle; //一覧のタイトルを設定
			if (subTitle != null) {
				bw.append("［＃中見出し］");
				printText(bw, subTitle);
//				bw.append('\n');
				bw.append("［＃中見出し終わり］\n");
			}
			//公開日付
			String coutentUpdate = getExtractText(doc, this.queryMap.get(ExtractId.CONTENT_UPDATE));
			if (coutentUpdate != null && !coutentUpdate.isEmpty()) postDate = coutentUpdate;
			if (postDate != null) {
				bw.append("［＃ここから地から１字上げ］\n［＃ここから１段階小さな文字］\n");
				printText(bw, postDate);
				bw.append('\n');
				bw.append("［＃ここで小さな文字終わり］\n［＃ここで字上げ終わり］\n");
			}

			bw.append('\n');

			//画像 前に表示
			Elements images = getExtractElements(doc, this.queryMap.get(ExtractId.CONTENT_IMG));
			if (images != null) printImage(bw, images.getFirst());

			//前書き
			Elements preambleDivs = getExtractElements(doc, this.queryMap.get(ExtractId.CONTENT_PREAMBLE));
			if (preambleDivs != null) {
				Element startElement = getExtractFirstElement(doc, this.queryMap.get(ExtractId.CONTENT_PREAMBLE_START));
				Element endElement = getExtractFirstElement(doc, this.queryMap.get(ExtractId.CONTENT_PREAMBLE_END));
				bw.append("［＃区切り線］\n");
				bw.append("［＃ここから２字下げ］\n［＃ここから２字上げ］\n");
				bw.append("［＃ここから１段階小さな文字］\n");
				bw.append("\n");
				for (Element elem : preambleDivs) printNode(bw, elem, startElement, endElement, true);
				bw.append("\n\n");
				bw.append("［＃ここで小さな文字終わり］\n");
				bw.append("［＃ここで字上げ終わり］\n［＃ここで字下げ終わり］\n");
				bw.append("［＃区切り線］\n");
				bw.append("\n\n");
                preambleDivs.remove();
			}
			//本文
			String separator = null;
            contentDivs = getExtractElements(doc, this.queryMap.get(ExtractId.CONTENT_ARTICLE));
			ExtractInfo[] separatorInfo = this.queryMap.get(ExtractId.CONTENT_ARTICLE_SEPARATOR);
			if (separatorInfo != null && separatorInfo.length > 0) {
				separator = separatorInfo[0].query;
			}
			boolean first = true;
			for (Element elem : contentDivs) {
				//複数のDivの場合は間に改行追加
				if (first) first = false;
				else {
					bw.append("\n");
					if (separator != null) bw.append(separator);
				}
				Element startElement = getExtractFirstElement(doc, this.queryMap.get(ExtractId.CONTENT_ARTICLE_START));
				Element endElement = getExtractFirstElement(doc, this.queryMap.get(ExtractId.CONTENT_ARTICLE_END));
				printNode(bw, elem, startElement, endElement, false);
			}

			//後書き
			Elements appendixDivs = getExtractElements(doc, this.queryMap.get(ExtractId.CONTENT_APPENDIX));
			if (appendixDivs != null) {
				Element startElement = getExtractFirstElement(doc, this.queryMap.get(ExtractId.CONTENT_APPENDIX_START));
				Element endElement = getExtractFirstElement(doc, this.queryMap.get(ExtractId.CONTENT_APPENDIX_END));
				bw.append("\n\n");
				bw.append("［＃区切り線］\n");
				bw.append("［＃ここから２字下げ］\n［＃ここから２字上げ］\n");
				bw.append("［＃ここから１段階小さな文字］\n");
				bw.append("\n");
				for (Element elem : appendixDivs) printNode(bw, elem, startElement, endElement, true);
				bw.append("\n");
				bw.append("［＃ここで小さな文字終わり］\n");
				bw.append("［＃ここで字上げ終わり］\n［＃ここで字下げ終わり］\n");
			}
		}
	}

	Node startElement = null;
	Node endElement = null;
	boolean noHr = false;
	/** ノードを出力 子ノード内のテキストも出力 */
	private void printNode(BufferedWriter bw, Node parent, boolean noHr) throws IOException
	{
		printNode(bw, parent, null, null, noHr);
	}
	/** ノードを出力 子ノード内のテキストも出力 */
	private void printNode(BufferedWriter bw, Node parent, Node start, Node end, boolean noHr) throws IOException
	{
		this.startElement = start;
		this.endElement = end;
		this.noHr = noHr;
		_printNode(bw, parent);
	}
	/** ノードを出力 再帰用 */
	private void _printNode(BufferedWriter bw, Node parent) throws IOException
	{
		for (Node node : parent.childNodes()) {
			if (startElement != null) {
				if (node.equals(startElement)) {
					startElement = null;
					continue;
				}
				if (node instanceof Element) _printNode(bw, node);
				continue;
			}
			if (node.equals(endElement)) {
				return;
			}
			if (node instanceof TextNode) printText(bw, ((TextNode)node).getWholeText());
			else if (node instanceof Element elem) {
                if ("br".equals(elem.tagName())) {
					if (elem.nextSibling() != null) bw.append('\n');
				} else if ("div".equals(elem.tagName())) {
					if (elem.previousSibling() != null && !isBlockNode(elem.previousSibling())) bw.append('\n');
					_printNode(bw, node); //子を出力
					if (elem.nextSibling() != null) bw.append('\n');
				} else if ("p".equals(elem.tagName())) {
					//if (elem.previousSibling() != null && !isBlockNode(elem.previousSibling())) ;
					_printNode(bw, node); //子を出力
					if (elem.nextSibling() != null) bw.append('\n');
				} else if ("ruby".equals(elem.tagName())) {
					//ルビ注記出力
					printRuby(bw, elem);
				} else if ("img".equals(elem.tagName())) {
					//画像をキャッシュして注記出力
					printImage(bw, elem);
				} else if ("hr".equals(elem.tagName()) && !this.noHr) {
					bw.append("［＃区切り線］\n");
				} else if ("b".equals(elem.tagName())) {
					bw.append("［＃ここから太字］");
					_printNode(bw, node); //子を出力
					bw.append("［＃ここで太字終わり］");
				} else if ("em".equals(elem.tagName())) {
					bw.append("［＃丸傍点］");
					_printNode(bw, node); //子を出力
					bw.append("［＃丸傍点終わり］");
				}else if ("sup".equals(elem.tagName())) {
					bw.append("［＃上付き小文字］");
					_printNode(bw, node); //子を出力
					bw.append("［＃上付き小文字終わり］");
				} else if ("sub".equals(elem.tagName())) {
					bw.append("［＃下付き小文字］");
					_printNode(bw, node); //子を出力
					bw.append("［＃下付き小文字終わり］");
				} else if ("strike".equals(elem.tagName()) || "s".equals(elem.tagName()) ) {
					bw.append("［＃取消線］");
					_printNode(bw, node); //子を出力
					bw.append("［＃取消線終わり］");
				} else if ("tr".equals(elem.tagName())) {
					_printNode(bw, node); //子を出力
					bw.append('\n');
				} else {
					_printNode(bw, node); //子を出力
				}
			} else {
				System.out.println(node.getClass().getName());
			}
		}
	}
	/** 前がブロック注記かどうか */
	private boolean isBlockNode(Node node)
	{
		if (node instanceof Element elem) {
            String tagName = elem.tagName();
            return switch (tagName) {
                case "br", "div", "p", "hr" -> true;
                default -> "table".equals(tagName);
            };
        }
		return false;
	}

	/** ルビを青空ルビにして出力 */
	private void printRuby(BufferedWriter bw, Element ruby) throws IOException
	{

		for (Node childNode : ruby.childNodes()) {
			if (childNode instanceof Element element) {
                switch (element.nodeName()) {
					case "rp":
						break;
					case "rt":
						bw.append('《');
						printText(bw, element.text());
						bw.append('》');
						break;
					case "rb":
						bw.append('｜');
						printText(bw, element.text());
						break;
					default:
						System.out.println("ruby error");
				}
			} else if (childNode instanceof TextNode element) {
                bw.append('｜');
				printText(bw, element.text());
			}
		}
	}
	/** 画像をキャッシュして相対パスの注記にする */
	private void printImage(BufferedWriter bw, Element img) throws IOException
	{
		this.printImage(bw, img, null);
	}
	/** 画像をキャッシュして相対パスの注記にする
	 * @param bw nullなら注記文字列は出力しない
	 * @param img imgタグ
	 * @param imageOutFile null出なければこのファイルに画像を出力 */
	private void printImage(BufferedWriter bw, Element img, File imageOutFile) throws IOException
	{
		String src = img.attr("src");
		if (src.isEmpty()) return;

		String imagePath;
		int idx = src.indexOf("//");
		if (idx > 0) {
			imagePath = CharUtils.escapeUrlToFile(src.substring(idx+2));
		} else if(idx == 0 && src.indexOf("mitemin") != 0) { // なろう様 changes to new image provider
			src = "https:" + src;
            if(webLageImage) {
                String ImageUrl = src.replace("userpageimage/viewimagebig/icode/", "");
                String imagePagePath = CharUtils.escapeUrlToFile(ImageUrl);
                File imagePageFile = new File(this.dstPath + "images/" + imagePagePath + "/index.html");
                try {
                    cacheFile(ImageUrl, imagePageFile, this.urlString);
                    Document imagepagedoc = Jsoup.parse(imagePageFile, null);
                    ImageUrl = imagepagedoc.getElementsByClass("imageview").first().children().attr("href");
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }
                src = ImageUrl;
            }
            imagePath = CharUtils.escapeUrlToFile(src);
		} else if (src.charAt(0) == '/') {
			imagePath = "_"+CharUtils.escapeUrlToFile(src);
			src = this.baseUri+src;
		}
		else {
			imagePath = "__/"+CharUtils.escapeUrlToFile(src);
			if (this.pageBaseUri.endsWith("/")) src = this.pageBaseUri+src;
			else src = this.pageBaseUri+"/"+src;
		}

		if (imagePath.endsWith("/")) imagePath += "image.png";

		File imageFile = new File(this.dstPath+"images/"+imagePath);

		LogAppender.append("画像を取得してみます：" + src);
		try {
			if (imageOutFile != null) {
				if (imageOutFile.exists()) imageOutFile.delete();
				cacheFile(src, imageOutFile, this.urlString);
			} else if (!imageFile.exists()) {
				cacheFile(src, imageFile, this.urlString);
			}
		} catch (Exception e) {
			e.printStackTrace();
			LogAppender.println("画像が取得できませんでした : "+src);
		}
		LogAppender.println(" - 完了みたいです。");

		if (bw != null) {
			bw.append("［＃挿絵（");
			bw.append("images/").append(imagePath);
			bw.append("）入る］\n");
		}
	}

	/** 文字を出力 特殊文字は注記に変換 */
	private void printText(BufferedWriter bw, String text) throws IOException
	{
		char[] chars = text.toCharArray();
		for (char ch : chars) {
			//青空特殊文字
			switch (ch) {
			case '《': bw.append("※［＃始め二重山括弧、1-1-52］"); break;
			case '》': bw.append("※［＃終わり二重山括弧、1-1-53］"); break;
			case '［': bw.append("※［＃始め角括弧、1-1-46］"); break;
			case '］': bw.append("※［＃終わり角括弧、1-1-47］"); break;
			case '〔': bw.append("※［＃始め亀甲括弧、1-1-44］"); break;
			case '〕': bw.append("※［＃終わり亀甲括弧、1-1-45］"); break;
			case '｜': bw.append("※［＃縦線、1-1-35］"); break;
			case '＃': bw.append("※［＃井げた、1-1-84］"); break;
			case '※': bw.append("※［＃米印、1-2-8］"); break;
			case '\t': bw.append(' '); break;
			case '\n':
			case '\r': break;
			default: bw.append(ch);
			}
		}
	}

	////////////////////////////////////////////////////////////////

	/** Element内のinnerHTMLを取得
	 * 間にあるダグは無視 置換設定があれば置換してから返す */
	String getExtractText(Document doc, ExtractInfo[] extractInfos)
	{
		return getExtractText(doc, extractInfos, true);
	}
	String getExtractText(Document doc, ExtractInfo[] extractInfos, boolean replace)
	{
		if (extractInfos == null) return null;
		for (ExtractInfo extractInfo : extractInfos) {
			String text = null;
			Elements elements = doc.select(extractInfo.query);
			if (elements.isEmpty()) continue;
			StringBuilder buf = new StringBuilder();
			if (extractInfo.idx == null) {
				for (Element element : elements) {
					String html = element.html();
                    buf.append(" ").append(replaceHtmlText(html, replace?extractInfo:null));
				}
			} else {
				for (int i=0; i<extractInfo.idx.length; i++) {
					if (elements.size() > extractInfo.idx[i]) {
						int pos = extractInfo.idx[i];
						if (pos < 0) pos = elements.size()+pos;//負の値なら後ろから
						if (pos >= 0 && elements.size() > pos) {
							String html = elements.get(pos).html();
                            buf.append(" ").append(replaceHtmlText(html, replace?extractInfo:null));
						}
					}
				}
				if (!buf.isEmpty()) text = buf.deleteCharAt(0).toString();
			}
			//置換指定ならreplaceして返す
			if (text != null && !text.isEmpty()) {
				return text;
			}
		}
		return null;
	}

	Vector<String> getExtractStrings(Document doc, ExtractInfo[] extractInfos, boolean replace)
	{
		if (extractInfos == null) return null;
		for (ExtractInfo extractInfo : extractInfos) {
			Elements elements = doc.select(extractInfo.query);
			if (elements.isEmpty()) continue;
			Vector<String> vecString = new Vector<String>();
			if (extractInfo.idx == null) {
				for (Element element : elements) {
					String html = element.html();
                    vecString.add(replaceHtmlText(html, replace?extractInfo:null));
				}
			} else {
				for (int i=0; i<extractInfo.idx.length; i++) {
					if (elements.size() > extractInfo.idx[i]) {
						int pos = extractInfo.idx[i];
						if (pos < 0) pos = elements.size()+pos;//負の値なら後ろから
						if (pos >= 0 && elements.size() > pos) {
							String html = elements.get(pos).html();
                            vecString.add(replaceHtmlText(html, replace?extractInfo:null));
						}
					}
				}
			}
			return vecString;
		}
		return null;
	}

	/** cssQueryに対応するノード内の文字列を取得 */
	String getQueryText(Document doc, String[] queries)
	{
		if (queries == null) return null;
		for (String query : queries) {
			String text  = getFirstText(doc.select(query).first());
			if (text != null && !text.isEmpty()) return text;
		}
		return null;
	}

	/** cssQueryに対応するノードを取得 前のQueryを優先 */
	Elements getExtractElements(Document doc, ExtractInfo[] extractInfos)
	{
		if (extractInfos == null) return null;
		for (ExtractInfo extractInfo : extractInfos) {
			Elements elements = doc.select(extractInfo.query);
			if (elements.isEmpty()) continue;
			if (extractInfo.idx == null) return elements;
			Elements e2 = new Elements();
			for (int i=0; i<extractInfo.idx.length; i++) {
				int pos = extractInfo.idx[i];
				if (pos < 0) pos = elements.size()+pos;//負の値なら後ろから
				if (pos >= 0 && elements.size() > pos) e2.add(elements.get(pos));
			}
			if (!e2.isEmpty()) return e2;
		}
		return null;
	}

	/** cssQueryに対応するノードを取得 前のQueryを優先 */
	Element getExtractFirstElement(Document doc, ExtractInfo[] extractInfos)
	{
		if (extractInfos == null) return null;
		for (ExtractInfo extractInfo : extractInfos) {
			Elements elements = doc.select(extractInfo.query);
			if (elements.isEmpty()) continue;
			int pos = extractInfo.idx[0];
			if (pos < 0) pos = elements.size()+pos;//負の値なら後ろから
			if (pos >= 0 && elements.size() > pos) return elements.get(pos);
		}
		return null;
	}

	/** タグの直下の最初のテキストを取得 */
	String getFirstText(Element elem)
	{
		if (elem != null) {
			List<TextNode> nodes = elem.textNodes();
			for (TextNode node : nodes) {
				if (node != null) {
					String text = node.getWholeText();
					if (!text.isEmpty()) {
						text = text.replaceAll("[\n|\r]", "").replaceAll("\t", " ").trim();
						if (!text.isEmpty()) return text;
					}
				}
			}
		}
		return null;
	}

	////////////////////////////////////////////////////////////////

	String replaceHtmlText(String text, ExtractInfo extractInfo) {
		text = text.replaceAll("[\n|\r]", "").replaceAll("\t", " ");
		if (extractInfo != null) text = extractInfo.replace(text);
		return Jsoup.parse(removeTag(text)).text();
	}

	/** タグを除去 rt内の文字は非表示 */
	String removeTag(String text)
	{
		return text.replaceAll("<br ?/?>", " ").replaceAll("<rt>.+?</rt>", "").replaceAll("<[^>]+>", "");
	}

	////////////////////////////////////////////////////////////////
	/**
	 * htmlをキャッシュ すでにあれば何もしない
	 */
	private void cacheFile(String urlString, File cacheFile, String referer) throws IOException, URISyntaxException
	{
		//if (!replace && cacheFile.exists()) return false;
		try { if (cacheFile.isDirectory()) cacheFile.delete(); } catch (Exception e) {} //空のディレクトリなら消す
		if (cacheFile.isDirectory()) { LogAppender.println("フォルダがあるためキャッシュできません : "+cacheFile.getAbsolutePath()); }
		//フォルダ以外がすでにあったら削除
		File parentFile = cacheFile.getParentFile();
		if (parentFile.exists() && !parentFile.isDirectory()) {
			parentFile.delete();
		}
		cacheFile.getParentFile().mkdirs();
		//ダウンロード
		URLConnection conn = new URI(urlString).toURL().openConnection();
		ExtractInfo[] ua = this.queryMap.get(ExtractId.USER_AGENT);
        if (this.UserAgent != null) conn.setRequestProperty("User-Agent", this.UserAgent);
		if (ua != null && ua.length > 0) conn.setRequestProperty("User-Agent", ua[0].query);
		ExtractInfo[] cookie = this.queryMap.get(ExtractId.COOKIE);
		if (cookie != null && cookie.length > 0) conn.setRequestProperty("Cookie", cookie[0].query);
		if (referer != null) conn.setRequestProperty("Referer", referer);
		conn.setConnectTimeout(10000);//10秒
		conn.setReadTimeout(10000);//10秒
		BufferedInputStream bis = new BufferedInputStream(conn.getInputStream(), 8192);
		BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(cacheFile));
		//IOUtils.copy(bis, bos);
		bis.transferTo(bos);
		bos.close();
		bis.close();
	}
}
