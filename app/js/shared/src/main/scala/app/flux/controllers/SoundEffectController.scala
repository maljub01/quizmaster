package app.flux.controllers

import app.flux.router.AppPages
import app.models.access.ModelFields
import app.models.quiz.Team
import hydro.flux.action.Action
import hydro.flux.action.Dispatcher
import hydro.flux.action.StandardActions
import hydro.flux.router.Page
import hydro.jsfacades.Audio
import hydro.models.access.JsEntityAccess
import hydro.models.modification.EntityModification

import scala.collection.immutable.Seq
import scala.collection.mutable
import scala.scalajs.js

class SoundEffectController(
    implicit dispatcher: Dispatcher,
    entityAccess: JsEntityAccess,
) {
  private var currentPage: Page = _
  private val soundsPlaying: mutable.Set[SoundEffect] = mutable.Set()

  dispatcher.registerPartialSync(dispatcherListener)
  entityAccess.registerListener(JsEntityAccessListener)

  // **************** Public API ****************//
  //

  // **************** Private helper methods ****************//
  private def dispatcherListener: PartialFunction[Action, Unit] = {
    case StandardActions.SetPageLoadingState( /* isLoading = */ _, currentPage) =>
      this.currentPage = currentPage
  }

  private def canPlaySoundEffectsOnThisPage: Boolean = currentPage == AppPages.Quiz

  private def playSoundEffect(soundEffect: SoundEffect, unlessAlreadyPlaying: Boolean = false): Unit = {
    if (unlessAlreadyPlaying && (soundsPlaying contains soundEffect)) {
      // Skip
    } else {
      // TODO: soundsPlaying.add(soundEffect)
      val audio = new Audio(soundEffect.filepath)
      audio.play()
      // TODO: soundsPlaying.remove(soundEffect)
    }
  }

  private object JsEntityAccessListener extends JsEntityAccess.Listener {
    override def modificationsAddedOrPendingStateChanged(modifications: Seq[EntityModification]): Unit = {
      modifications.collect {
        case EntityModification.Update(team: Team) =>
          if (Some(team.lastUpdateTime.mostRecentInstant) ==
                team.lastUpdateTime.timePerField.get(ModelFields.Team.score)) {
            playSoundEffect(SoundEffect.ScoreIncreased, unlessAlreadyPlaying = true)
          }
      }
    }
  }

  private sealed abstract class SoundEffect(val filepath: String)
  private object SoundEffect {
    case object ScoreIncreased extends SoundEffect("soundeffects/score_increased.mp3")
  }
}