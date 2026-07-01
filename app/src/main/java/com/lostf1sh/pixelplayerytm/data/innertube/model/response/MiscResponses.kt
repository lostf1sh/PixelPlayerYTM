package com.lostf1sh.pixelplayerytm.data.innertube.model.response

import kotlinx.serialization.Serializable

@Serializable
data class VisitorIdResponse(
    val responseContext: ResponseContext? = null,
)

@Serializable
data class AccountMenuResponse(
    val actions: List<Action>? = null,
) {
    val header: ActiveAccountHeaderRenderer?
        get() = actions?.firstNotNullOfOrNull {
            it.openPopupAction?.popup?.multiPageMenuRenderer?.header?.activeAccountHeaderRenderer
        }

    @Serializable
    data class Action(
        val openPopupAction: OpenPopupAction? = null,
    ) {
        @Serializable
        data class OpenPopupAction(
            val popup: Popup? = null,
        ) {
            @Serializable
            data class Popup(
                val multiPageMenuRenderer: MultiPageMenuRenderer? = null,
            ) {
                @Serializable
                data class MultiPageMenuRenderer(
                    val header: Header? = null,
                ) {
                    @Serializable
                    data class Header(
                        val activeAccountHeaderRenderer: ActiveAccountHeaderRenderer? = null,
                    )
                }
            }
        }
    }

    @Serializable
    data class ActiveAccountHeaderRenderer(
        val accountName: Runs? = null,
        val email: Runs? = null,
        val accountPhoto: Thumbnails? = null,
    )
}
