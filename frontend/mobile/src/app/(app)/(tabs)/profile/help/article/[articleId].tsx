import { useLocalSearchParams, useRouter } from 'expo-router';

import { HelpArticle } from '@/screens/ProfileScreen';

export default function HelpArticleRoute() {
  const router = useRouter();
  const params = useLocalSearchParams<{ articleId?: string | string[] }>();
  const articleId = Array.isArray(params.articleId)
    ? params.articleId[0]
    : params.articleId ?? '';
  return <HelpArticle articleId={articleId} onBack={() => router.back()} />;
}
