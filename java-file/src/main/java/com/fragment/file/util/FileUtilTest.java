package com.fragment.file.util;

import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * @Author huabin
 * @DateTime 2025-09-04 15:32
 * @Desc
 */
public class FileUtilTest {

    public static MultipartFile fileToMultipartFile(File file) throws IOException {
        MultipartFile multipartFile = null;
        FileInputStream fileInputStream = null;
        try{
            fileInputStream = new FileInputStream(file);
            multipartFile = new MockMultipartFile(file.getName(), file.getName(), "application/octet-stream", fileInputStream);
        } catch (Exception e) {

        } finally {
            fileInputStream.close();
        }
        return multipartFile;
    }

}
