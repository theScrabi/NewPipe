package org.schabi.newpipe.local.subscription

import androidx.annotation.DrawableRes
import org.schabi.newpipe.R

enum class FeedGroupIcon(
    /**
     * The id that will be used to store and retrieve icons from some persistent storage (e.g. DB).
     */
    val id: Int,

    /**
     * The drawable resource.
     */
    @DrawableRes val drawableResource: Int
) {
    ALL(0, R.drawable.ic_asterisk),
    MUSIC(1, R.drawable.ic_music_note),
    EDUCATION(2, R.drawable.ic_school),
    FITNESS(3, R.drawable.ic_fitness_center),
    SPACE(4, R.drawable.ic_telescope),
    COMPUTER(5, R.drawable.ic_computer),
    GAMING(6, R.drawable.ic_videogame_asset),
    SPORTS(7, R.drawable.ic_directions_bike),
    NEWS(8, R.drawable.ic_megaphone),
    FAVORITES(9, R.drawable.ic_favorite),
    CAR(10, R.drawable.ic_directions_car),
    MOTORCYCLE(11, R.drawable.ic_motorcycle),
    TREND(12, R.drawable.ic_trending_up),
    MOVIE(13, R.drawable.ic_movie),
    BACKUP(14, R.drawable.ic_backup),
    ART(15, R.drawable.ic_palette),
    PERSON(16, R.drawable.ic_person),
    PEOPLE(17, R.drawable.ic_people),
    MONEY(18, R.drawable.ic_attach_money),
    KIDS(19, R.drawable.ic_child_care),
    FOOD(20, R.drawable.ic_fastfood),
    SMILE(21, R.drawable.ic_insert_emoticon),
    EXPLORE(22, R.drawable.ic_explore),
    RESTAURANT(23, R.drawable.ic_restaurant),
    MIC(24, R.drawable.ic_mic),
    HEADSET(25, R.drawable.ic_headset),
    RADIO(26, R.drawable.ic_radio),
    SHOPPING_CART(27, R.drawable.ic_shopping_cart),
    WATCH_LATER(28, R.drawable.ic_watch_later),
    WORK(29, R.drawable.ic_work),
    HOT(30, R.drawable.ic_whatshot),
    CHANNEL(31, R.drawable.ic_tv),
    BOOKMARK(32, R.drawable.ic_bookmark),
    PETS(33, R.drawable.ic_pets),
    WORLD(34, R.drawable.ic_public),
    STAR(35, R.drawable.ic_stars),
    SUN(36, R.drawable.ic_wb_sunny),
    RSS(37, R.drawable.ic_rss_feed);

    @DrawableRes
    fun getDrawableRes(): Int {
        return drawableResource
    }
}
