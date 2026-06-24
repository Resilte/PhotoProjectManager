package com.photomanager.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * 百度网盘服务
 * 使用百度网盘开放API：https://pan.baidu.com/union
 *
 * ===== 配置步骤 =====
 * 1. 在菜单栏点击「百度网盘 → 配置应用」，填入 App Key 和 Secret Key
 *    （也可以直接修改 data/app_config.properties 文件）
 * 2. 点击「百度网盘 → 登录授权」，按提示操作
 *
 * 申请地址：https://pan.baidu.com/union
 * 创建应用时，应用类型选"桌面应用"，回调地址填：oob
 */
public class BaiduNetdiskService {

    // 从 AppConfig 读取配置，无需硬编码
    private String getAppKey() {
        return AppConfig.getInstance().getBaiduAppKey();
    }

    private String getSecretKey() {
        return AppConfig.getInstance().getBaiduSecretKey();
    }

    // 回调地址："oob"(Out-of-Band) 适用于桌面应用
    private static final String REDIRECT_URI = "oob";

    private String accessToken;
    private final Gson gson = new Gson();

    public BaiduNetdiskService() {
        // 从配置文件恢复 access token
        String savedToken = AppConfig.getInstance().getBaiduAccessToken();
        if (savedToken != null && savedToken.length() > 0) {
            this.accessToken = savedToken;
        } else {
            this.accessToken = null;
        }
    }

    /**
     * 检查是否已配置 App Key / Secret Key
     */
    public boolean isConfigured() {
        String key = getAppKey();
        String secret = getSecretKey();
        return key != null && key.length() > 0
            && secret != null && secret.length() > 0;
    }

    /**
     * 获取 OAuth2.0 授权 URL
     * 使用 "oob" 模式时，百度会在授权页面直接显示授权码，用户手动复制粘贴即可
     */
    public String getAuthUrl() {
        if (!isConfigured()) {
            return "ERROR: 请先在菜单「百度网盘 → 配置应用」中填写 App Key 和 Secret Key";
        }
        try {
            return "https://openapi.baidu.com/oauth/2.0/authorize?"
                + "response_type=code"
                + "&client_id=" + getAppKey()
                + "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, "UTF-8")
                + "&scope=basic,netdisk"
                + "&display=page";
        } catch (UnsupportedEncodingException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * 用授权码换取 access_token，并保存到配置文件
     */
    public boolean exchangeToken(String authCode) throws IOException {
        if (!isConfigured()) return false;
        String url = "https://openapi.baidu.com/oauth/2.0/token?"
            + "grant_type=authorization_code"
            + "&code=" + URLEncoder.encode(authCode, "UTF-8")
            + "&client_id=" + getAppKey()
            + "&client_secret=" + getSecretKey()
            + "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, "UTF-8");

        String response = httpGet(url);
        JsonObject json = gson.fromJson(response, JsonObject.class);

        if (json.has("access_token")) {
            this.accessToken = json.get("access_token").getAsString();
            // 保存到配置文件
            AppConfig.getInstance().setBaiduAccessToken(this.accessToken);
            AppConfig.getInstance().save();
            return true;
        }
        return false;
    }

    /**
     * 设置已有的 access_token（用于手动配置）
     */
    public void setAccessToken(String token) {
        this.accessToken = token;
        AppConfig.getInstance().setBaiduAccessToken(token);
        AppConfig.getInstance().save();
    }

    public String getAccessToken() {
        return accessToken;
    }

    public boolean isAuthorized() {
        return accessToken != null && !accessToken.isEmpty();
    }

    /**
     * 获取用户信息，验证 token 有效性
     */
    public String getUserInfo() throws IOException {
        if (!isAuthorized()) throw new IOException("未授权，请先登录百度网盘");
        return httpGet("https://pan.baidu.com/rest/2.0/xpan/nas?method=uinfo&access_token=" + accessToken);
    }

    /**
     * 上传文件到百度网盘
     * @param localFilePath 本地文件路径
     * @param remotePath 百度网盘中的路径，如 /apps/PhotoManager/file.zip
     */
    public String uploadFile(String localFilePath, String remotePath) throws IOException {
        if (!isAuthorized()) throw new IOException("未授权，请先登录百度网盘");
        File file = new File(localFilePath);
        if (!file.exists()) throw new IOException("文件不存在: " + localFilePath);

        String path = URLEncoder.encode(remotePath, "UTF-8");

        // 预上传
        String precreateUrl = "https://pan.baidu.com/rest/2.0/xpan/file?"
            + "method=precreate&access_token=" + accessToken;
        String precreateBody = "path=" + path
            + "&size=" + file.length()
            + "&isdir=0&autoinit=1&rtype=3&block_list=[\""
            + getFileMd5(file) + "\"]";

        String preResponse = httpPost(precreateUrl, precreateBody);
        JsonObject preJson = gson.fromJson(preResponse, JsonObject.class);
        if (preJson.has("errno") && preJson.get("errno").getAsInt() != 0) {
            return "预上传失败: " + preResponse;
        }

        String uploadId = preJson.get("uploadid").getAsString();

        // 上传文件内容
        String uploadUrl = "https://d.pcs.baidu.com/rest/2.0/pcs/superfile2?"
            + "method=upload&access_token=" + accessToken
            + "&type=tmpfile&path=" + path
            + "&uploadid=" + uploadId
            + "&partseq=0";

        String uploadResponse = uploadFilePart(uploadUrl, file);
        JsonObject uploadJson = gson.fromJson(uploadResponse, JsonObject.class);

        if (uploadJson.has("md5")) {
            // 创建文件
            String createUrl = "https://pan.baidu.com/rest/2.0/xpan/file?"
                + "method=create&access_token=" + accessToken;
            String createBody = "path=" + path
                + "&size=" + file.length()
                + "&isdir=0&uploadid=" + uploadId
                + "&block_list=[\"" + uploadJson.get("md5").getAsString() + "\"]&rtype=3";

            String createResponse = httpPost(createUrl, createBody);
            return "上传完成: " + createResponse;
        }

        return "上传结果: " + uploadResponse;
    }

    /**
     * 生成分享链接
     */
    public String createShareLink(String remotePath) throws IOException {
        if (!isAuthorized()) throw new IOException("未授权，请先登录百度网盘");

        String url = "https://pan.baidu.com/rest/2.0/xpan/share?"
            + "method=create&access_token=" + accessToken;
        String body = "path=" + URLEncoder.encode(remotePath, "UTF-8")
            + "&period=0&link_type=0";

        String response = httpPost(url, body);
        JsonObject json = gson.fromJson(response, JsonObject.class);

        if (json.has("link")) {
            return json.get("link").getAsString();
        }
        return "创建分享链接失败: " + response;
    }

    // ==================== HTTP 工具方法 ====================

    private String httpGet(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        return readResponse(conn);
    }

    private String httpPost(String urlStr, String body) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes("UTF-8"));
        }
        return readResponse(conn);
    }

    private String readResponse(HttpURLConnection conn) throws IOException {
        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) return "HTTP " + code;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private String uploadFilePart(String urlStr, File file) throws IOException {
        String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(60000);
        conn.setReadTimeout(60000);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (OutputStream os = conn.getOutputStream()) {
            String header = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + file.getName() + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n";
            os.write(header.getBytes("UTF-8"));

            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    os.write(buffer, 0, len);
                }
            }

            os.write(("\r\n--" + boundary + "--\r\n").getBytes("UTF-8"));
        }

        return readResponse(conn);
    }

    private String getFileMd5(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                md.update(buffer, 0, len);
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
