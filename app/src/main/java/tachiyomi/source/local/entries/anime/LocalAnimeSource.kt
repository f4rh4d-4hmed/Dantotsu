package tachiyomi.source.local.entries.anime

import android.content.Context
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.UnmeteredSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import eu.kanade.tachiyomi.util.storage.DiskUtil
//import eu.kanade.tachiyomi.util.storage.toFFmpegString
import kotlinx.serialization.json.Json
import rx.Observable
import tachiyomi.core.util.lang.withIOContext
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.source.local.filter.anime.AnimeOrderBy
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.util.concurrent.TimeUnit

class LocalAnimeSource(
    private val context: Context,
) : AnimeCatalogueSource, UnmeteredSource {

    private val POPULAR_FILTERS = AnimeFilterList(AnimeOrderBy.Popular(context))
    private val LATEST_FILTERS = AnimeFilterList(AnimeOrderBy.Latest(context))

    override val name ="Local anime source"

    override val id: Long = ID

    override val lang = "other"

    override fun toString() = name

    override val supportsLatest = true

    // Browse related
    override fun fetchPopularAnime(page: Int) = fetchSearchAnime(page, "", POPULAR_FILTERS)

    override fun fetchLatestUpdates(page: Int) = fetchSearchAnime(page, "", LATEST_FILTERS)

    override fun fetchSearchAnime(page: Int, query: String, filters: AnimeFilterList): Observable<AnimesPage> {
        //return emptyObservable()
        return Observable.just(AnimesPage(emptyList(), false))
    }

    // Anime details related
    override suspend fun getAnimeDetails(anime: SAnime): SAnime = withIOContext {
        //return empty
        anime
    }

    // Episodes
    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        //return empty
        return emptyList()
    }

    // Filters
    override fun getFilterList() = AnimeFilterList(AnimeOrderBy.Popular(context))

    // Unused stuff
    override suspend fun getVideoList(episode: SEpisode) = throw UnsupportedOperationException("Unused")

    companion object {
        const val ID = 0L
        const val HELP_URL = "https://aniyomi.org/help/guides/local-anime/"

        private const val DEFAULT_COVER_NAME = "cover.jpg"
        private val LATEST_THRESHOLD = TimeUnit.MILLISECONDS.convert(7, TimeUnit.DAYS)

        private fun getBaseDirectories(context: Context): Sequence<File> {
            val localFolder = "Aniyomi" + File.separator + "localanime"
            return DiskUtil.getExternalStorages(context)
                .map { File(it.absolutePath, localFolder) }
                .asSequence()
        }

        private fun getBaseDirectoriesFiles(context: Context): Sequence<File> {
            return getBaseDirectories(context)
                // Get all the files inside all baseDir
                .flatMap { it.listFiles().orEmpty().toList() }
        }

        private fun getAnimeDir(animeUrl: String, baseDirsFile: Sequence<File>): File? {
            return baseDirsFile
                // Get the first animeDir or null
                .firstOrNull { it.isDirectory && it.name == animeUrl }
        }
    }
}

fun Anime.isLocal(): Boolean = source == LocalAnimeSource.ID

fun AnimeSource.isLocal(): Boolean = id == LocalAnimeSource.ID