package com.waz.sync.handler

import com.waz.content.{ConversationStorage, MembersStorage, MessagesStorage}
import com.waz.model._
import com.waz.service.conversation.{ConversationOrderEventsService, ConversationsContentUpdater, ConversationsService}
import com.waz.service.messages.MessagesService
import com.waz.service.{ConversationRolesService, ErrorsService, GenericMessageService, UserService}
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.client.ConversationsClient.ConversationResponse
import com.waz.sync.client.ConversationsClient.ConversationResponse.ConversationsResult
import com.waz.sync.client.{ConversationsClient, ErrorOr, ErrorOrResponse}
import com.wire.signals.{CancellableFuture, Signal}

import scala.concurrent.Future

class ConversationsSyncHandlerSpec extends AndroidFreeSpec {

  private val self = UserData("self")
  private val teamId = TeamId()
  private val userService      = mock[UserService]
  private val messagesStorage = mock[MessagesStorage]
  private val messagesService = mock[MessagesService]
  private val convService = mock[ConversationsService]
  private val convs = mock[ConversationsContentUpdater]
  private val convEvents = mock[ConversationOrderEventsService]
  private val convStorage = mock[ConversationStorage]
  private val errorsService = mock[ErrorsService]
  private val conversationsClient = mock[ConversationsClient]
  private val genericMessages = mock[GenericMessageService]
  private val rolesService = mock[ConversationRolesService]
  private val membersStorage = mock[MembersStorage]

  private def createHandler: ConversationsSyncHandler = new ConversationsSyncHandler(
    self.id, Some(teamId), userService, messagesStorage, messagesService,
    convService, convs, convEvents, convStorage, errorsService,
    conversationsClient, genericMessages, rolesService, membersStorage
  )

  private def toConversationResponse(conv: ConversationData) =
    ConversationResponse(
      conv.remoteId, conv.name, conv.creator, conv.convType, conv.team,
      conv.muted, conv.muteTime, conv.archived, conv.archiveTime,
      conv.access, conv.accessRole, conv.link, None, Map.empty, conv.receiptMode
    )

  scenario("When syncing conversations, given local convs that are absent from the backend, remove the missing local convs") {
    val conv1 = ConversationData(team = Some(teamId), creator = self.id)
    val conv2 = ConversationData(team = Some(teamId), creator = self.id)
    val conv3 = ConversationData(team = Some(teamId), creator = self.id)

    val resp1 = toConversationResponse(conv1)
    val resp2 = toConversationResponse(conv2)

    val backendResponse: ErrorOrResponse[ConversationsResult] =
      CancellableFuture.successful { Right(ConversationsResult(Seq(resp1, resp2), hasMore = false)) }
    val storageResponse =
      Future.successful(Set(conv1.remoteId, conv2.remoteId, conv3.remoteId))

    (conversationsClient.loadConversations(_: Option[RConvId], _: Int)).expects(*, *).anyNumberOfTimes().returning(backendResponse)
    (convService.remoteIds _).expects().anyNumberOfTimes().returning(storageResponse)

    (rolesService.defaultRoles _).expects().anyNumberOfTimes().returning(Signal.const(Set.empty[ConversationRole]))
    (conversationsClient.loadConversationRoles _).expects(*).anyNumberOfTimes().returning(Future.successful(Map.empty[RConvId, Set[ConversationRole]]))
    (convService.updateConversationsWithDeviceStartMessage _).expects(*, *).anyNumberOfTimes().returning(Future.successful(()))

    (convService.deleteConversation _).expects(conv3.remoteId).once().returning(Future.successful(()))
    val handler = createHandler
    result(handler.syncConversations())
  }

}
