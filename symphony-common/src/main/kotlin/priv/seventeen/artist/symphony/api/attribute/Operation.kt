package priv.seventeen.artist.symphony.api.attribute

enum class Operation {
    FLAT,
    PERCENT;

    companion object {
        /**
         * 把 YAML 配置里的字符串解析成 [Operation]。
         * 不区分大小写，仅判首字符（P/p → PERCENT，其它 → FLAT），避免每次创建大写新字符串。
         * 空串 / null 视为 FLAT（向后兼容）。
         */
        @JvmStatic
        fun parse(raw: String?): Operation {
            if (raw.isNullOrEmpty()) return FLAT
            return if (raw[0] == 'P' || raw[0] == 'p') PERCENT else FLAT
        }
    }
}
