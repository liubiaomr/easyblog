package top.easyblog.autoconfig;

import com.google.gson.Gson;
import com.qiniu.common.QiniuException;
import com.qiniu.http.Response;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.model.DefaultPutRet;
import com.qiniu.util.Auth;
import com.qiniu.util.StringMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * @author HuangXin
 * @since 2019/12/29 14:38
 */
public class QiNiuCloudService {

    @Autowired
    private QiNiuCloudProperties qiNiuCloudProperties;

    private Logger log = LoggerFactory.getLogger(QiNiuCloudService.class);

    /**
     * 获取上传凭证
     *
     * @return
     */
    private String getUpToken() {
        Auth auth = Auth.create(qiNiuCloudProperties.getAccessKey(), qiNiuCloudProperties.getSecretKey());
        return auth.uploadToken(qiNiuCloudProperties.getBucketName(), null, 3600, new StringMap().put("insertOnly", 1));
    }

    /**
     * 本地文件上传
     *
     * @param filePath 文件路径
     * @param fileName 文件名
     * @return 返回文件的外链URL
     * @throws IOException
     */
    public String upload(String filePath, String fileName) throws IOException {
        try {
            Auth auth = Auth.create(qiNiuCloudProperties.getAccessKey(), qiNiuCloudProperties.getSecretKey());
            String token = auth.uploadToken(qiNiuCloudProperties.getBucketName());
            Response response = qiNiuCloudProperties.getUploadManager().put(filePath, fileName, token);
            log.info("return info：{}", response.bodyString());
            int retry = 3;
            while (response.needRetry() && retry > 0) {
                response = qiNiuCloudProperties.getUploadManager().put(filePath, fileName, token);
                retry--;
            }
            if (response.isOK()) {
                QiNiuCloudService.Ret ret = response.jsonToObject(QiNiuCloudService.Ret.class);
                //如果不需要对图片进行样式处理，则使用以下方式即可
                return qiNiuCloudProperties.getStyle() == null ? ("http://" + qiNiuCloudProperties.getDomain() + "/" + ret.key) : ("http://" + qiNiuCloudProperties.getDomain() + "/" + ret.key + "?" + qiNiuCloudProperties.getStyle());
            }
        } catch (QiniuException e) {
            Response response = e.response;
            log.error("Exception：{}", response.toString());
            try {
                log.error("Exception ：{}", response.bodyString());
            } catch (QiniuException e1) {
                // ignore
            }
        }
        return "上传失败";
    }


    /**
     * 以MultipartFile的形式上传
     *
     * @param multipartFile 前端提交过来的文件数据
     * @return 返回图片直接可访问的URL
     */
    public String putMultipartImage(MultipartFile multipartFile) {
        //解析文件后缀名
        String originalFilename = multipartFile.getOriginalFilename();
        assert originalFilename != null;
        String suffix = originalFilename.substring(originalFilename.lastIndexOf("."), originalFilename.length());
        String key = System.currentTimeMillis() + UUID.randomUUID().toString() + suffix;
        try {
            byte[] uploadBytes = multipartFile.getBytes();
            putBase64Image(uploadBytes, key);
        } catch (IOException e) {
            throw new RuntimeException("上传图片失败");
        }
        return qiNiuCloudProperties.getStyle() == null ? ("http://" + qiNiuCloudProperties.getDomain() + "/" + key) : ("http://" + qiNiuCloudProperties.getDomain() + "/" + key + "?" + qiNiuCloudProperties.getStyle());
    }

    /**
     * 以字节数组(byte[])的形式上传
     *
     * @param bytes 字节数组，图片数据
     * @param key   图片的名字 如果为null，将会把图片的hashcode作为名字
     * @return 返回图片直接可访问的URL
     */
    public String putBase64Image(byte[] bytes, String key) {
        String upToken = getUpToken();
        DefaultPutRet putRet = null;
        try {
            Response response = qiNiuCloudProperties.getUploadManager().put(bytes, key, upToken);
            int retry = 3;
            while (response.needRetry() && retry > 0) {
                retry--;
                response = qiNiuCloudProperties.getUploadManager().put(bytes, key, upToken);
            }
            //解析上传成功的结果
            putRet = new Gson().fromJson(response.bodyString(), DefaultPutRet.class);
        } catch (QiniuException ex) {
            Response r = ex.response;
            System.err.println(r.toString());
            try {
                System.err.println(r.bodyString());
            } catch (QiniuException ex2) {
                throw new RuntimeException("上传图片失败");
            }
        }
        assert putRet != null;
        return null == key ? ("http://" + qiNiuCloudProperties.getDomain() + "/" + putRet.hash) : ("http://" + qiNiuCloudProperties.getDomain() + "/" + key);
    }


    /**
     * 删除在七牛云上的文件
     *
     * @param imageUrl 文件URL
     */
    public void delete(String imageUrl) {
        String key = imageUrl.substring(imageUrl.lastIndexOf("/") + 1, imageUrl.length());
        // 实例化一个BucketManager对象
        Auth auth = Auth.create(qiNiuCloudProperties.getAccessKey(), qiNiuCloudProperties.getSecretKey());
        BucketManager bucketManager = new BucketManager(auth, qiNiuCloudProperties.getConfiguration());
        try {
            // 调用delete方法移动文件
            bucketManager.delete(qiNiuCloudProperties.getBucketName(), key);
        } catch (QiniuException e) {
            // 捕获异常信息
            Response response = e.response;
            log.error(response.toString());
        }
    }

    /***封装文件上传后的返回结果***/
    static final class Ret {
        public long fsize;
        public String key;
        public String hash;
        public int width;
        public int height;

        public long getFsize() {
            return fsize;
        }

        public void setFsize(long fsize) {
            this.fsize = fsize;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getHash() {
            return hash;
        }

        public void setHash(String hash) {
            this.hash = hash;
        }

        public int getWidth() {
            return width;
        }

        public void setWidth(int width) {
            this.width = width;
        }

        public int getHeight() {
            return height;
        }

        public void setHeight(int height) {
            this.height = height;
        }
    }

}
