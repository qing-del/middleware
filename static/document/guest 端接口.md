# 个人SaaS中台项目


**简介**:个人SaaS中台项目


**HOST**:http://localhost:8080


**联系人**:


**Version**:0.0.1


**接口路径**:/v3/api-docs/guest 端接口


[TOC]






# Guest-公开笔记


## 分页查询公开笔记


**接口地址**:`/guest/note`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|dto|访客公开笔记查询请求|query|true|GuestNoteQueryDTO|GuestNoteQueryDTO|
|&emsp;&emsp;keyword|搜索关键词（标题模糊搜索）||false|string||
|&emsp;&emsp;topicId|主题ID||false|integer(int64)||
|&emsp;&emsp;pageNum|页码（默认1）||false|integer(int32)||
|&emsp;&emsp;pageSize|每页大小（默认15）||false|integer(int32)||
|&emsp;&emsp;pageSizeOrDefault|||false|integer(int32)||
|&emsp;&emsp;pageNumOrDefault|||false|integer(int32)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultPageResult|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||PageResult|PageResult|
|&emsp;&emsp;total||integer(int64)||
|&emsp;&emsp;records||array|object|


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"total": 0,
		"records": []
	}
}
```


## 查看公开笔记详情


**接口地址**:`/guest/note/{noteId}`


**请求方式**:`GET`


**请求数据类型**:`application/x-www-form-urlencoded`


**响应数据类型**:`*/*`


**接口描述**:


**请求参数**:


| 参数名称 | 参数说明 | 请求类型    | 是否必须 | 数据类型 | schema |
| -------- | -------- | ----- | -------- | -------- | ------ |
|noteId|笔记ID|path|true|integer(int64)||


**响应状态**:


| 状态码 | 说明 | schema |
| -------- | -------- | ----- | 
|200|OK|ResultGuestNoteDetailVO|


**响应参数**:


| 参数名称 | 参数说明 | 类型 | schema |
| -------- | -------- | ----- |----- | 
|code||integer(int32)|integer(int32)|
|msg||string||
|data||GuestNoteDetailVO|GuestNoteDetailVO|
|&emsp;&emsp;id||integer(int64)||
|&emsp;&emsp;topicId||integer(int64)||
|&emsp;&emsp;topicName||string||
|&emsp;&emsp;title||string||
|&emsp;&emsp;description||string||
|&emsp;&emsp;tags||array|string|
|&emsp;&emsp;images||array|ImageSimpleVO|
|&emsp;&emsp;&emsp;&emsp;imageId||integer||
|&emsp;&emsp;&emsp;&emsp;noteId||integer||
|&emsp;&emsp;&emsp;&emsp;parsedImageName||string||
|&emsp;&emsp;&emsp;&emsp;filename||string||
|&emsp;&emsp;&emsp;&emsp;ossUrl||string||
|&emsp;&emsp;&emsp;&emsp;isPublic||integer||
|&emsp;&emsp;&emsp;&emsp;isPass||integer||
|&emsp;&emsp;&emsp;&emsp;isCrossUser||integer||
|&emsp;&emsp;&emsp;&emsp;isMissing||integer||
|&emsp;&emsp;&emsp;&emsp;createTime||string||
|&emsp;&emsp;eachNotes||array|NoteEachSimpleVO|
|&emsp;&emsp;&emsp;&emsp;targetNoteId||integer||
|&emsp;&emsp;&emsp;&emsp;targetNoteTitle||string||
|&emsp;&emsp;&emsp;&emsp;parsedNoteName||string||
|&emsp;&emsp;&emsp;&emsp;anchor||string||
|&emsp;&emsp;&emsp;&emsp;nickname||string||
|&emsp;&emsp;&emsp;&emsp;isMissing||integer||
|&emsp;&emsp;converted||NoteConvertResultVO|NoteConvertResultVO|
|&emsp;&emsp;&emsp;&emsp;meta|标题、标签、创建时间|NoteConvertMetaVO|NoteConvertMetaVO|
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;title||string||
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;tags||array|string|
|&emsp;&emsp;&emsp;&emsp;&emsp;&emsp;createTime||string||
|&emsp;&emsp;&emsp;&emsp;tocHtml|目录 HTML - (可以用于创建标题跳转栏)|string||
|&emsp;&emsp;&emsp;&emsp;bodyHtml|正文 HTML|string||
|&emsp;&emsp;createTime||string(date-time)||
|&emsp;&emsp;updateTime||string(date-time)||


**响应示例**:
```javascript
{
	"code": 0,
	"msg": "",
	"data": {
		"id": 0,
		"topicId": 0,
		"topicName": "",
		"title": "",
		"description": "",
		"tags": [],
		"images": [
			{
				"imageId": 0,
				"noteId": 0,
				"parsedImageName": "",
				"filename": "",
				"ossUrl": "",
				"isPublic": 0,
				"isPass": 0,
				"isCrossUser": 0,
				"isMissing": 0,
				"createTime": ""
			}
		],
		"eachNotes": [
			{
				"targetNoteId": 0,
				"targetNoteTitle": "",
				"parsedNoteName": "",
				"anchor": "",
				"nickname": "",
				"isMissing": 0
			}
		],
		"converted": {
			"meta": {
				"title": "",
				"tags": [],
				"createTime": ""
			},
			"tocHtml": "",
			"bodyHtml": ""
		},
		"createTime": "",
		"updateTime": ""
	}
}
```