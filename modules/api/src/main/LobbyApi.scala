package lila.api

import play.api.libs.json.{ Json, JsObject, JsArray }

import lila.common.LightUser
import lila.common.PimpedJson._
import lila.game.{ GameRepo, Pov }
import lila.lobby.SeekApi
import lila.setup.FilterConfig
import lila.user.{ User, UserContext }

final class LobbyApi(
    getFilter: UserContext => Fu[FilterConfig],
    lightUser: String => Option[LightUser],
    seekApi: SeekApi,
    pools: List[lila.pool.PoolConfig]) {

  import makeTimeout.large

  private val poolsJson = JsArray {
    pools.map { p =>
      Json.obj(
        "id" -> p.id.value,
        "lim" -> p.clock.limitInMinutes,
        "inc" -> p.clock.increment,
        "perf" -> p.perfType.name)
    }
  }

  def apply(implicit ctx: Context): Fu[JsObject] =
      ctx.me.fold(seekApi.forAnon)(seekApi.forUser) zip
      (ctx.me ?? GameRepo.urgentGames) zip
      getFilter(ctx) map {
        case ((seeks, povs), filter) => Json.obj(
          "me" -> ctx.me.map { u =>
            Json.obj("username" -> u.username)
          },
          "seeks" -> JsArray(seeks map (_.render)),
          "nowPlaying" -> JsArray(povs take 9 map nowPlaying),
          "nbNowPlaying" -> povs.size,
          "filter" -> filter.render,
          "pools" -> poolsJson)
      }

  def nowPlaying(pov: Pov) = Json.obj(
    "fullId" -> pov.fullId,
    "gameId" -> pov.gameId,
    "fen" -> (chess.format.Forsyth exportBoard pov.game.toChess.board),
    "color" -> pov.color.name,
    "lastMove" -> ~pov.game.castleLastMoveTime.lastMoveString,
    "variant" -> Json.obj(
      "key" -> pov.game.variant.key,
      "name" -> pov.game.variant.name),
    "speed" -> pov.game.speed.key,
    "perf" -> lila.game.PerfPicker.key(pov.game),
    "rated" -> pov.game.rated,
    "opponent" -> Json.obj(
      "id" -> pov.opponent.userId,
      "username" -> lila.game.Namer.playerString(pov.opponent, withRating = false)(lightUser),
      "rating" -> pov.opponent.rating,
      "ai" -> pov.opponent.aiLevel).noNull,
    "isMyTurn" -> pov.isMyTurn,
    "secondsLeft" -> pov.remainingSeconds)
}
