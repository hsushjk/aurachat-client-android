package dev.aurachat.client.net

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** 全是同步调用，调用方自己开线程。 */
object SettingsApi {
    fun changePassword(api: AuraApi, old: String, new: String): JSONObject =
        api.call("api/account/password", JSONObject().put("oldPassword", old).put("newPassword", new))

    fun stateMe(api: AuraApi): JSONObject = api.call("api/state")

    fun config(api: AuraApi): JSONObject = api.call("api/config")

    fun patchConfig(api: AuraApi, body: JSONObject): JSONObject = api.call("api/config", body, "PATCH")

    fun status(api: AuraApi): JSONObject = api.call("api/status")

    fun logs(api: AuraApi): JSONObject = api.call("api/logs")

    /** 可选模型：`{ok, models:[{provider, model} | {provider, models:[…]}]}`。 */
    fun models(api: AuraApi): JSONObject = api.call("api/models")

    fun clearLogs(api: AuraApi): JSONObject = api.call("api/logs/clear", JSONObject(), "POST")

    /** what ∈ speakup / diary / selfdrive / social */
    fun test(api: AuraApi, what: String): JSONObject = api.call("api/test", JSONObject().put("what", what))

    fun mood(api: AuraApi): JSONObject = api.call("api/mood")

    fun nudgeMood(api: AuraApi, delta: Int, reason: String): JSONObject =
        api.call("api/mood", JSONObject().put("delta", delta).put("reason", reason))

    fun resetMood(api: AuraApi): JSONObject = api.call("api/mood", JSONObject().put("reset", true))

    fun memory(api: AuraApi): JSONObject = api.call("api/memory")

    fun addMemory(api: AuraApi, text: String, importance: Double = 0.6, source: String = "manual"): JSONObject =
        api.call(
            "api/memory",
            JSONObject().put("text", text).put("source", source).put("importance", importance).put("tags", JSONArray()),
        )

    // about 定位印象写给谁，text 是内容。
    fun memoryProfile(api: AuraApi, about: String, text: String, name: String = ""): JSONObject =
        api.call("api/memory/profile", JSONObject().put("about", about).put("text", text).put("name", name))

    fun memoryInsight(api: AuraApi, key: String, name: String, lines: List<String>): JSONObject =
        api.call("api/memory/insight", JSONObject().put("key", key).put("name", name).put("lines", JSONArray(lines)))

    /** 相处经验：整组覆盖（服务端只认 lines 文本数组，每条会补新的 id/at）。 */
    fun memoryInsightRaw(api: AuraApi, key: String, name: String, list: JSONArray): JSONObject {
        val lines = JSONArray()
        for (i in 0 until list.length()) {
            val t = list.optJSONObject(i)?.optString("text").orEmpty()
            if (t.isNotBlank()) lines.put(t)
        }
        return api.call("api/memory/insight", JSONObject().put("key", key).put("name", name).put("lines", lines))
    }

    fun memoryStyle(api: AuraApi, who: String, lines: List<String>): JSONObject =
        api.call("api/memory/style", JSONObject().put("who", who).put("lines", JSONArray(lines)))

    fun deleteMemory(api: AuraApi, id: String): JSONObject =
        api.call("api/memory/delete", JSONObject().put("id", id))

    fun updateMemory(api: AuraApi, id: String, text: String): JSONObject =
        api.call("api/memory/update", JSONObject().put("id", id).put("text", text))

    fun consolidateMemory(api: AuraApi): JSONObject =
        api.call("api/memory/consolidate", JSONObject().put("force", true))

    fun diary(api: AuraApi): JSONObject = api.call("api/diary")

    fun diaryComment(api: AuraApi, entryId: String, text: String): JSONObject =
        api.call("api/diary/comment", JSONObject().put("entryId", entryId).put("text", text))

    fun diaryReply(api: AuraApi, entryId: String, commentId: String, text: String): JSONObject =
        api.call(
            "api/diary/reply",
            JSONObject().put("entryId", entryId).put("commentId", commentId).put("text", text),
        )

    fun diaryCommentDelete(api: AuraApi, entryId: String, commentId: String, replyId: String = ""): JSONObject =
        api.call(
            "api/diary/comment/delete",
            JSONObject().put("entryId", entryId).put("commentId", commentId).put("replyId", replyId),
        )

    fun diaryDelete(api: AuraApi, entryId: String): JSONObject =
        api.call("api/diary/delete", JSONObject().put("entryId", entryId))

    fun diaryRun(api: AuraApi, visibility: String = "all"): JSONObject =
        api.call("api/diary/run", JSONObject().put("visibility", visibility))

    fun diarySocialRun(api: AuraApi): JSONObject = api.call("api/diary/social-run", JSONObject(), "POST")

    fun personas(api: AuraApi): JSONObject = api.call("api/personas")

    fun personaDetail(api: AuraApi, id: String): JSONObject =
        api.call("api/personas/detail?id=" + URLEncoder.encode(id, "UTF-8"))

    fun personaSave(api: AuraApi, id: String, name: String, tags: String, body: String): JSONObject =
        api.call(
            "api/personas/save",
            JSONObject().put("id", id).put("name", name).put("tags", tags).put("body", body),
        )

    fun personaDelete(api: AuraApi, id: String): JSONObject =
        api.call("api/personas/delete", JSONObject().put("id", id))

    fun guests(api: AuraApi): JSONObject = api.call("api/guests")

    fun addGuest(api: AuraApi, username: String, displayName: String, note: String, level: String): JSONObject =
        api.call(
            "api/guests",
            JSONObject().put("username", username).put("displayName", displayName).put("note", note).put("level", level),
        )

    fun patchGuest(api: AuraApi, subject: String, body: JSONObject): JSONObject =
        api.call("api/guests/" + URLEncoder.encode(subject, "UTF-8"), body, "PATCH")

    fun deleteGuest(api: AuraApi, subject: String): JSONObject =
        api.call("api/guests/" + URLEncoder.encode(subject, "UTF-8"), null, "DELETE")

    fun stickers(api: AuraApi): JSONObject = api.call("api/stickers")

    fun uploadSticker(api: AuraApi, base64: String, name: String): JSONObject =
        api.call("api/stickers/upload", JSONObject().put("data", base64).put("name", name))

    fun deleteSticker(api: AuraApi, md5: String): JSONObject =
        api.call("api/stickers/" + md5, null, "DELETE")

    fun renameSticker(api: AuraApi, md5: String, name: String, tags: List<String>): JSONObject =
        api.call(
            "api/stickers/" + md5,
            JSONObject().put("name", name).put("tags", JSONArray(tags)),
            "PATCH",
        )

    fun avatarBytes(api: AuraApi): ByteArray? = api.fetchBytes("api/avatar")

    fun uploadAvatar(api: AuraApi, base64: String, mime: String): JSONObject =
        api.call("api/avatar", JSONObject().put("data", base64).put("mime", mime))

    fun selfDrive(api: AuraApi): JSONObject = api.call("api/selfdrive")

    fun setSelfDrive(api: AuraApi, text: String): JSONObject =
        api.call("api/selfdrive", JSONObject().put("text", text))

    /** 返回 {ok, engine, mime, data(base64)}。 */
    fun tts(api: AuraApi, text: String): JSONObject =
        api.call("api/voice/test", JSONObject().put("text", text))

    fun edgeInstalled(api: AuraApi): JSONObject = api.call("api/voice/edge")

    /** 装 edge-tts（POST，无 body）；返回 {ok}。 */
    fun edgeInstall(api: AuraApi): JSONObject = api.call("api/voice/edge", JSONObject())
}
