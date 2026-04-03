# instructions：
## Configure environment variable of the AliyunOSS
> Since the `AliOssUtil` class written by me adopts a method to read environment variable to obtain value
> So the `OSS_ACCESS_KEY_ID` and `OSS_ACCESS_KEY_SECRET` environment variable need to be configured.
1. Set environment variable
```cmd
set OSS_ACCESS_KEY_ID=你的AccessKeyId
set OSS_ACCESS_KEY_SECRET=你的Secret
```
2. Apply the preset environment variable
> ```cmd
> setx OSS_ACCESS_KEY_ID "%OSS_ACCESS_KEY_ID%"
> setx OSS_ACCESS_KEY_SECRET "%OSS_ACCESS_KEY_SECRET%"
> ```
3. Check the environment variable
> ```cmd
> echo %OSS_ACCESS_KEY_ID%
> echo %OSS_ACCESS_KEY_SECRET%
> ```