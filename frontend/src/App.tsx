import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import { Layout } from './components/Layout';
import { FeedView } from './views/FeedView';
import { CommunityView } from './views/CommunityView';
import { ProfileView } from './views/ProfileView';
import { BookmarkView } from './views/BookmarkView';
import { PostDetailView } from './views/PostDetailView';
import { CommunitiesView } from './views/CommunitiesView';
import { UserProfileView } from './views/UserProfileView';
import { SearchView } from './views/SearchView';
import { AdminView } from './views/AdminView';

export default function App() {
  return (
    <Router>
      <Layout>
        <Routes>
          <Route path="/" element={<FeedView />} />
          <Route path="/trending" element={<FeedView />} />
          <Route path="/new" element={<FeedView />} />
          <Route path="/community/:id" element={<CommunityView />} />
          <Route path="/communities" element={<CommunitiesView />} />
          <Route path="/post/:id" element={<PostDetailView />} />
          <Route path="/settings" element={<ProfileView />} />
          <Route path="/user/:username" element={<UserProfileView />} />
          <Route path="/bookmarks" element={<BookmarkView />} />
          <Route path="/search" element={<SearchView />} />
          <Route path="/admin" element={<AdminView />} />
        </Routes>
      </Layout>
    </Router>
  );
}
