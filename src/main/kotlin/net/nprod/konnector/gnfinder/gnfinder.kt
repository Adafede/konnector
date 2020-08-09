package net.nprod.konnector.gnfinder

import com.google.protobuf.util.JsonFormat
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import protob.GNFinderGrpcKt
import protob.Gnfinder
import java.io.Closeable
import java.util.concurrent.TimeUnit

@Serializable
data class GNFinderBestResult(
    val dataSourceTitle: String = "",
    val taxonId: String = "",
    val matchedCanonicalFull: String = ""
)

@Serializable
data class GNFinderVerification(
    val bestResult: GNFinderBestResult? = null
)

@Serializable
data class GNFinderNames(
    val name: String,
    val verification: GNFinderVerification? = null
)

@Serializable
data class GNFinderResponse(
    val names: List<GNFinderNames>? = listOf()
)


fun voidRequest(): Gnfinder.Void = Gnfinder.Void.newBuilder().build()

/**
 * Connect to a local GNFinder instance accessible by gRPC
 */
class GNFinderClient(val target: String, private val dispatcher: ExecutorCoroutineDispatcher) : Closeable {
    private val channel =
        ManagedChannelBuilder.forTarget(target).usePlaintext().executor(dispatcher.asExecutor()).build()
    private val stub: GNFinderGrpcKt.GNFinderCoroutineStub = GNFinderGrpcKt.GNFinderCoroutineStub(channel)

    fun ping(): String = runBlocking {
        stub.ping(voidRequest()).value
    }

    fun ver(): String = runBlocking {
        stub.ver(voidRequest()).version
    }

    fun findNames(
        query: String,
        language: String = "english",
        sources: Iterable<Int> = listOf(),
        verification: Boolean = false
    ): String {
        val message = runBlocking {
            val params = Gnfinder.Params.newBuilder()
            params.language = language
            params.text = query
            params.verification = verification
            params.addAllSources(sources)
            stub.findNames(params.build())
        }
        val printer = JsonFormat.printer()
        return printer.print(message)
    }

    fun findNamesToStructured(
        query: String,
        language: String = "english",
        sources: Iterable<Int> = listOf(),
        verification: Boolean = false
    ): GNFinderResponse {
        val json = Json(
            JsonConfiguration(
                ignoreUnknownKeys = true,
                isLenient = true
            )
        )
        return json.parse(
            GNFinderResponse.serializer(),
            string = findNames(query, language, sources, verification)
        )
    }

    override fun close() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }
}