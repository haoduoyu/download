package com.download.util;

import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author rain
 * @Version 1.0.0
 * @ClassName DownloadUtil
 * @Description 下载工具
 * @CreateTime 2020/9/22 20:27
 */
public class DownloadUtil {

    // 下载地址
    private String downloadUrl = null;

    // 默认线程数
    private Integer DEFAULT_THREAD_NUM = 10;

    // 线程数
    private Integer threadNum = null;

    // 默认下载分段长度
    private Integer DEFAULT_SEGMENT_LENGTH = 1024;

    // 下载时候进行分段的段长度
    private Integer segmentLength = null;

    // 文件下载路径
    private String targetPath = null;

    // url
    private URL url = null;

    // 将要下载的文件连接
    private URLConnection connection = null;

    // 下载的连接池
    private ExecutorService threadPool = null;

    // 计数器，可直接使用random形式存储至文件
    CountDownLatch countDownLatch = null;

    // 记录将要写入文件的字节信息
    Map<Integer, byte[]> downloadResultMap = new ConcurrentHashMap<Integer, byte[]>();

    // 用于计算进度
    private AtomicInteger completedCount = null;

    // MIME 信息
    private static Map<String, String> mimeMap = null;

    private Boolean flag = true;

    static {
        if (null == mimeMap) {
            // 此作用域代码仅做填充数据，无实际参考价值，不要在这块纠结我的写法
            mimeMap = new HashMap<>();
            try {
                File file = new File("./mime");
                FileReader fr = new FileReader(file);
                BufferedReader br = new BufferedReader(fr);
                br.lines().forEach(item -> {
                    String temp = item.trim().replace("'", "").replace(",", "");
                    String[] tempArr = temp.split("=>");
                    mimeMap.put(tempArr[1], tempArr[0]);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 初始化相关参数
     *
     * @param downloadUrl   下载地址
     * @param threadNum     下载线程数，默认为10
     * @param segmentLength 下载片段长度，默认1024
     * @throws Exception
     */
    public DownloadUtil(String downloadUrl, Integer threadNum, Integer segmentLength, String targetPath) {

        if (StringUtils.isBlank(downloadUrl)) {
            System.out.println("downloadUrl 不可为空");
            return;
        }

        this.threadNum = threadNum;
        if (null == threadNum || threadNum < 0) {
            this.threadNum = DEFAULT_THREAD_NUM;
        }

        this.segmentLength = segmentLength;
        if (null == segmentLength || segmentLength < 0) {
            this.segmentLength = DEFAULT_SEGMENT_LENGTH;
        }

        this.downloadUrl = downloadUrl;
        this.targetPath = targetPath;

        this.threadPool = Executors.newFixedThreadPool(this.threadNum);
        this.completedCount = new AtomicInteger(0);
        this.flag = true;

        try {
            this.url = new URL(this.downloadUrl);
            this.connection = this.url.openConnection();
            this.connection.setRequestProperty("Content-Type", "application/octet-stream");
            this.connection.setDoOutput(true);
            this.connection.setDoInput(true);
            this.connection.setRequestProperty("Connection", "Keep-Alive");
            this.connection.connect();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 开始下载
     *
     * @return
     */
    public void startDownload() {
        try {

            final int fileLength = connection.getContentLength();

            StringBuilder sb = new StringBuilder();
            sb.append("文件 MIME 类型：").append(connection.getContentType())
                    .append("\n文件大小为：").append(fileLength);
            Map<String, List<String>> headerFields = connection.getHeaderFields();

            sb.append("\n文件头信息：\n");
            headerFields.forEach((k, v) -> {
                sb.append("\t").append(k).append(": ").append(v).append(";\n");
            });
            System.out.println(sb.toString());

            if (this.segmentLength == null || this.segmentLength < 0) {
                this.segmentLength = DEFAULT_SEGMENT_LENGTH;
            }

            // 计算应该分多少段进行下载
            int segments = fileLength / this.segmentLength;
            segments = fileLength % this.segmentLength == 0 ? segments : segments + 1;

            System.out.printf("需要分 %d 段进行下载\n", segments);

            countDownLatch = new CountDownLatch(segments);

            for (int segmentIndex = 0; segmentIndex < segments; segmentIndex++) {
                threadPool.execute(new DownloadThread(url, segments, segmentIndex, fileLength, connection.getContentType()));
            }

            countDownLatch.await();

            outputToFile(connection.getContentType());
            threadPool.shutdown();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 将信息写入文件
     *
     * @throws IOException
     */
    private void outputToFile(String contentType) throws IOException {

        if (!flag) {
            System.out.println("下载失败，不能生成");
            return;
        }

        int segments = downloadResultMap.size();
        String fileName = Calendar.getInstance().getTimeInMillis() + "" + (int) (Math.random() * 100) + "." + mimeMap.get(contentType);
        String path = StringUtils.isNotBlank(this.targetPath) ? this.targetPath + File.pathSeparator + fileName : "./" + fileName; // 略显粗糙的组合
        try (OutputStream os = new FileOutputStream(new File(path))) {

            for (int i = 0; i < segments; i++) {
                byte[] b = downloadResultMap.get(i);
                os.write(b);
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }
    }

    class DownloadThread implements Runnable {

        // 片段下标，用于记录所下载的指针点
        private int segmentIndex;

        // 片段总数
        private int segments;

        // 文件大小
        private int fileLength;

        // 请求到的片段流
        private InputStream downloadDataIS;

        public DownloadThread(URL url, int segments, int segmentIndex, int fileLength, String contentType) {
            this.segmentIndex = segmentIndex;
            this.segments = segments;
            this.fileLength = fileLength;

            try {
                initConnection(url, contentType);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /**
         * 初始化网络连接
         *
         * @param url
         * @throws IOException
         */
        private void initConnection(URL url, String contentType) throws IOException {
            URLConnection connection = url.openConnection();
            connection.setRequestProperty("Content-Type", contentType);
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setRequestProperty("Range", "bytes=" + (segmentIndex * segmentLength) + "-" +
                    ((
                            (segmentIndex + 1) * segmentLength - 1 - fileLength > 0 ?
                                    fileLength : (segmentIndex + 1) * segmentLength - 1
                    )));
            connection.setRequestProperty("Connection", "Keep-Alive");
            connection.connect();
            this.downloadDataIS = connection.getInputStream();
        }

        public void run() {
            byte[] b = new byte[segmentLength];
            try {
                if (downloadDataIS.read(b) != -1) {
                    downloadResultMap.put(this.segmentIndex, b);
                    System.out.printf("已完成进度的 %.2f%%\n", (100.0 * completedCount.incrementAndGet() / this.segments));
                }
            } catch (Exception e) {
                flag = false;
                e.printStackTrace();
            } finally {
                countDownLatch.countDown();
            }
        }
    }
}