import { useLocalSearchParams, useRouter } from 'expo-router';

import { routes } from '@/navigation/routes';
import { HelpCategory } from '@/screens/ProfileScreen';

export default function HelpCategoryRoute() {
  const router = useRouter();
  const params = useLocalSearchParams<{ categoryId?: string | string[] }>();
  const categoryId = Array.isArray(params.categoryId)
    ? params.categoryId[0]
    : params.categoryId ?? '';
  return (
    <HelpCategory
      categoryId={categoryId}
      onBack={() => router.back()}
      onOpenArticle={(articleId) => router.push(routes.profile.helpArticle(articleId))}
    />
  );
}
