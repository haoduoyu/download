package com.download;

import com.download.util.DownloadUtil;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @Author rain
 * @Version 1.0.0
 * @ClassName Main
 * @Description 自制简易下载工具程序入口
 * @CreateTime 2020/9/22 20:23
 */
public class Main {
    public static void main(String[] args) throws Exception {
//        String downloadUrl = "https://tse1-mm.cn.bing.net/th/id/OIP.zBOzCPD_oFueg3QTOiFKnwHaEK?w=290&h=180&c=7&o=5&pid=1.7";
        String downloadUrl = "https://appfile1.hicloud.com/FileServer/getFile/app/011/111/111/0000000000011111111.20200714180743.31692558386569719862016985227450:20471231000000:0001:8C4F81A70C650E37BA937E152AA43DB03EDBC97603FB2F038B4E4A89FF887804.zip?needInitFileName=true";
        int threadNum = 10;
        int segmentLength = 1024 * 1024;
        DownloadUtil downloadUtil = new DownloadUtil(downloadUrl, threadNum, segmentLength, null);

        downloadUtil.startDownload();

    }
}