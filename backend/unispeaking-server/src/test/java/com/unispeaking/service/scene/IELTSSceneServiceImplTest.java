package com.unispeaking.service.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.unispeaking.domain.po.scene.IeltsQuestion;
import com.unispeaking.domain.po.scene.IeltsTopic;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.vo.scene.IeltsPart;
import com.unispeaking.domain.vo.scene.IeltsTopicType;
import com.unispeaking.infrastructure.persistence.repository.scene.IeltsRepository;
import com.unispeaking.service.scene.impl.IELTSSceneServiceImpl;
import com.unispeaking.common.util.search.TitleRelevanceCalculator;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class IELTSSceneServiceImplTest {

	private final IeltsRepository repository = mock(IeltsRepository.class);
	private final IELTSSceneService service = new IELTSSceneServiceImpl(
			repository,
			new TitleRelevanceCalculator());

	@Test
	void partOneTrainingRandomlySelectsFourQuestions() {
		IeltsTopic topic = topic("home", IeltsTopicType.PART_1_POOL);
		when(repository.findTopicById(topic.id()))
				.thenReturn(Optional.of(topic));
		when(repository.findQuestions(topic.id(), IeltsPart.PART_1))
				.thenReturn(questions(topic.id(), IeltsPart.PART_1, 17));

		var result = service.prepareTraining(IeltsPart.PART_1, topic.id());

		assertEquals(4, result.questions().size());
		assertTrue(result.questions().stream()
				.allMatch(question -> question.part() == IeltsPart.PART_1));
	}

	@Test
	void partTwoAndPartThreeReturnTheirCompleteBoundQuestionSets() {
		IeltsTopic topic = topic("museum", IeltsTopicType.PART_2_3_BUNDLE);
		when(repository.findTopicById(topic.id()))
				.thenReturn(Optional.of(topic));
		when(repository.findQuestions(topic.id(), IeltsPart.PART_2))
				.thenReturn(questions(topic.id(), IeltsPart.PART_2, 1));
		when(repository.findQuestions(topic.id(), IeltsPart.PART_3))
				.thenReturn(questions(topic.id(), IeltsPart.PART_3, 6));

		var partTwo = service.prepareTraining(IeltsPart.PART_2, topic.id());
		var partThree = service.prepareTraining(IeltsPart.PART_3, topic.id());

		assertEquals(topic.id(), partTwo.topicId());
		assertEquals(topic.id(), partThree.topicId());
		assertEquals(1, partTwo.questions().size());
		assertEquals(6, partThree.questions().size());
	}

	@Test
	void relevanceSearchRanksMatchingTitlesAheadOfLooseMatches() {
		IeltsTopic exact = new IeltsTopic(
				"topic-home",
				"Home and Accommodation",
				IeltsTopicType.PART_1_POOL,
				"REQUIRED",
				"import",
				"ACTIVE");
		IeltsTopic loose = new IeltsTopic(
				"topic-hometown",
				"Hometown",
				IeltsTopicType.PART_1_POOL,
				"PLACE",
				"import",
				"ACTIVE");
		when(repository.findTopics(IeltsTopicType.PART_1_POOL))
				.thenReturn(List.of(loose, exact));
		when(repository.findQuestions(
				List.of(exact.id(), loose.id()),
				IeltsPart.PART_1))
				.thenReturn(List.of());

		var result = service.searchTopics(
				IeltsPart.PART_1,
				null,
				"home accommodation",
				1,
				10);

		assertEquals(exact.id(), result.topics().getFirst().id());
	}

	@Test
	void topicSearchReturnsTenItemsPerPageAndPaginationMetadata() {
		List<IeltsTopic> topics = IntStream.rangeClosed(1, 23)
				.mapToObj(index -> new IeltsTopic(
						"topic-" + index,
						"Topic " + index,
						IeltsTopicType.PART_1_POOL,
						index % 2 == 0 ? "REQUIRED" : "OBJECT",
						"import",
						"ACTIVE"))
				.toList();
		when(repository.findTopics(IeltsTopicType.PART_1_POOL))
				.thenReturn(topics);
		when(repository.findQuestions(
				topics.subList(10, 20).stream().map(IeltsTopic::id).toList(),
				IeltsPart.PART_1))
				.thenReturn(List.of());

		var result = service.searchTopics(
				IeltsPart.PART_1,
				"ALL",
				null,
				2,
				10);

		assertEquals(10, result.topics().size());
		assertEquals(2, result.page());
		assertEquals(10, result.pageSize());
		assertEquals(23, result.total());
		assertEquals(3, result.totalPages());
		assertEquals(2, result.categories().size());
	}

	@Test
	void searchCombinesKeywordMatchesWithRelatedTitles() {
		IeltsTopic direct = new IeltsTopic(
				"topic-music",
				"Music Lessons",
				IeltsTopicType.PART_1_POOL,
				"OBJECT",
				"import",
				"ACTIVE");
		IeltsTopic related = new IeltsTopic(
				"topic-musical",
				"Musical Instruments",
				IeltsTopicType.PART_1_POOL,
				"OBJECT",
				"import",
				"ACTIVE");
		IeltsTopic unrelated = new IeltsTopic(
				"topic-home",
				"Home and Accommodation",
				IeltsTopicType.PART_1_POOL,
				"REQUIRED",
				"import",
				"ACTIVE");
		when(repository.findTopics(IeltsTopicType.PART_1_POOL))
				.thenReturn(List.of(unrelated, related, direct));
		when(repository.findQuestions(
				List.of(direct.id(), related.id()),
				IeltsPart.PART_1))
				.thenReturn(List.of());

		var result = service.searchTopics(
				IeltsPart.PART_1,
				"ALL",
				" music lesson ",
				1,
				10);

		assertEquals(2, result.total());
		assertEquals(direct.id(), result.topics().getFirst().id());
		assertEquals(related.id(), result.topics().get(1).id());
	}

	@Test
	void invalidPaginationIsRejected() {
		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> service.searchTopics(
						IeltsPart.PART_1,
						null,
						null,
						0,
						10));

		assertEquals("IELTS_PAGINATION_INVALID", exception.code());
	}

	@Test
	void missingQuestionsAreRejected() {
		IeltsTopic topic = topic("empty", IeltsTopicType.PART_1_POOL);
		when(repository.findTopicById(topic.id())).thenReturn(Optional.of(topic));
		when(repository.findQuestions(topic.id(), IeltsPart.PART_1))
				.thenReturn(List.of());

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> service.prepareTraining(IeltsPart.PART_1, topic.id()));

		assertEquals("IELTS_QUESTIONS_NOT_FOUND", exception.code());
	}

	@Test
	void topicFromAnotherPartIsRejected() {
		IeltsTopic topic = topic("part-one", IeltsTopicType.PART_1_POOL);
		when(repository.findTopicById(topic.id())).thenReturn(Optional.of(topic));

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> service.prepareTraining(IeltsPart.PART_2, topic.id()));

		assertEquals("IELTS_PART_MISMATCH", exception.code());
	}

	private IeltsTopic topic(String id, IeltsTopicType type) {
		return new IeltsTopic(
				id,
				"Topic " + id,
				type,
				"REQUIRED",
				"import",
				"ACTIVE");
	}

	private List<IeltsQuestion> questions(
			String topicId,
			IeltsPart part,
			int count) {
		return IntStream.rangeClosed(1, count)
				.mapToObj(index -> new IeltsQuestion(
						topicId + "-" + part + "-" + index,
						topicId,
						part,
						index,
						"Question " + index,
						List.of(),
						List.of()))
				.toList();
	}
}
