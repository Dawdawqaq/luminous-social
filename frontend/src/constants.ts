import { Post, Community, TrendingTopic, User } from './types';

export const MOCK_USER: User = {
  id: 'felix_the_maker',
  username: 'felix_the_maker',
  displayName: '飞力创作',
  avatar: 'https://lh3.googleusercontent.com/aida-public/AB6AXuBoRZd-FzsWoXlu7TaZ4_Rr0J3mGa-EW5rPCBB10fI6EC5QjiGj64aSBAWrzUGXS0YxfUdriLp06d5tCbb73i-DZvuMa7qENuj5VALr5rntd6S-L89Lt5mfHofVjD6dAKk3sGLfOlg-CwEb1QPivIFNhPRBS05D9q4iVb1EqCNmzYCt9QC1EyEoOKmzDnSJYA6vNxkRbGQihop3w6u1tMcg7yk1_f3cMT879TazG5n8pzwq9zAZAxWkj0daOmS-7lD51q03mRHLQw',
  banner: 'https://lh3.googleusercontent.com/aida-public/AB6AXuBwgVSIktXOlux7JPz7yi1J5KYTcNHg9EXX1Q8NV7GzqKQmjbzIUbOovzu7cbhHlMxYdGpU1heQKGlSSuao5OKUnprLD6SEXdjy9fbZOgOfC9gE7YY0S4AoV6v0noKXtYwFhY7Q26u6kT1_3D5_3zrn2c4V89--R1gkmZ7SrvzlOj62XbYuFkeJw9XL5I66weaAQfaQLsQucTnhBRLKYhAOsKcHZsLt2OIDTi0oJ0b-O_d3u5NEaUErSYxVmFHUB5GqMG-7WeYycg',
  bio: '数字产品设计师与社区建设者。专注于构建模块化UI系统，探索艺术与实用的平衡边界。🎨✨',
  joinDate: '2024年3月',
  stats: {
    posts: 1200,
    followers: 8400,
    following: 432
  }
};

export const MOCK_COMMUNITY: Community = {
  id: 'pixel8',
  name: 'pixel8',
  displayName: 'Pixel 8 吧',
  icon: 'https://lh3.googleusercontent.com/aida-public/AB6AXuA0XE5NRvKbbDCmyatOsg7wItDiTm3ggv-QkP3tbSLeXZpfE8CfLcfKTHEGgv6vkgBhd0WqxvGmQw1ov2CTiM9uM3YvyUGjtuiEBmFhTitJkrlSvXRm-vSk-HifYN88cZ6i1iJ5s0pTIIldiRO0HKibXI7UBM2ihCEyHCWDYqmxa0ZaJ89LKJqmLq51tCFaiBbkJ5XfyFwwSECrxUmQnN6HYQA3PLwcq06VNVdzS_eP8GVe2Aok_-SeMIB3PaRoQzM0WldMpZ7Ipw',
  banner: 'https://lh3.googleusercontent.com/aida-public/AB6AXuA0XE5NRvKbbDCmyatOsg7wItDiTm3ggv-QkP3tbSLeXZpfE8CfLcfKTHEGgv6vkgBhd0WqxvGmQw1ov2CTiM9uM3YvyUGjtuiEBmFhTitJkrlSvXRm-vSk-HifYN88cZ6i1iJ5s0pTIIldiRO0HKibXI7UBM2ihCEyHCWDYqmxa0ZaJ89LKJqmLq51tCFaiBbkJ5XfyFwwSECrxUmQnN6HYQA3PLwcq06VNVdzS_eP8GVe2Aok_-SeMIB3PaRoQzM0WldMpZ7Ipw',
  description: 'A dedicated space for the Pixel 8 community. Share photos, discuss updates, find tips, and troubleshoot your Google device with enthusiasts.',
  membersCount: 142000,
  membersUnit: 'Members',
  rules: [
    'Be respectful to other members. No personal attacks.',
    'Posts must be relevant to the Pixel 8 family.',
    'No low-effort tech support requests; use the wiki.',
    'No affiliate links or unsolicited self-promotion.'
  ],
  moderators: [
    { id: '1', username: 'u/JayDev', initials: 'JD' },
    { id: '2', username: 'u/PixelLady', initials: 'PL' },
    { id: '3', username: 'u/ModK', initials: 'MK' }
  ]
};

export const MOCK_POSTS: Post[] = [
  {
    id: '1',
    author: { username: 'u/DesignSeeker', avatar: 'https://lh3.googleusercontent.com/aida-public/AB6AXuAAwEs18CWeq8gmZdLYl6aqcph3q-X8yhyBSceshzKCWC_rB0_vQOIAs8Y-IZN1brCKfGwBKMhpf9Ve-4FHqLNV0Lm-IpnujghJzjOnd3EwTKVrRGZuWjB1rmgA8Lsx8-2adIc7PxpBHgrPe5kgYWKbhkN1Ec-FMHaUO-x8c3fnNTkDZKXBf_hviK6AZcKGY7lZdoK8ZwamrPIQyuF7P-lzK-C_gcpuHBknIVi2_qwDPPkjx6-WrumCr6aoI89gQutAN_Of3sivNQ' },
    community: { name: 'r/IndustrialDesign' },
    title: '探索模块化家具系统的未来',
    content: "I've been working on this concept for a while now. The idea is to create a living space that breathes and adapts to the user's daily needs through magnetic attachment points and sustainable materials.",
    image: 'https://lh3.googleusercontent.com/aida-public/AB6AXuD4Uo9yEyjAMuLmCGdIFGxejzrdO6iG7EN4O8cq9p0Qfs5i3k_3EZJfv7pC8NEX0Kuoy-_D0a_1DYJi0J3Mw9yRNh1y5uBuJ8nKg62btKIeGLlP4Zoow26fxxYwZoUdq_cqe6odQDpZoGMsLJln08XiyfB0w51Ddi5bMyueybcw1PAWY2zHjrq9wiBcWXKWLSRGiJ11NwRlh6uw1KrnYW9_B0O7mMf-INPwscSi5SDrknK0Iua2rpOZguBr4_eccZe78m6JogrmXw',
    timestamp: '4小时前',
    votes: 154,
    comments: 154,
    type: 'standard'
  },
  {
    id: '2',
    author: { username: 'u/NeuroLinker', avatar: '' },
    community: { name: 'r/Futurism' },
    title: '为什么未来十年属于‘人机协作’系统',
    content: "As automation scales, the value of human judgment, empathy, and ethical oversight becomes the ultimate premium. We aren't being replaced; we are being elevated to orchestrators. Thoughts?",
    timestamp: '12小时前',
    votes: 892,
    comments: 42,
    type: 'standard'
  },
  {
    id: '3',
    author: { username: 'u/tech_enthusiast', avatar: '' },
    community: { name: 'r/pixel8' },
    title: 'Pixel 8 Pro 的夜景模式简直绝了！ 📸',
    content: '',
    image: 'https://lh3.googleusercontent.com/aida-public/AB6AXuA4hgzVALH8Kfg1WLrm-_bm2EL0TrZEfrEVLUWz-sviL7mytpHtEcBbsJlpi-2RoKN4vDq98nsYmGn2tRlw9iF1ac6C3CWGJ-YMbI6Eror0Gc_bbq5iEabAFD-T667ZADdVceubYKiH7IDDhw8dMseUvUjiLq88PQuDKMPbv5694iIGTVwCKwaevopIq3tlUUO6bSsMgENrIQ0CR1VrOJbg23FNXcDWy5xO5npRkRQ_Cuwopf-_NiNm9o4k-nHxAzC-arU9LasXdQ',
    timestamp: '2小时前',
    votes: 432,
    comments: 24,
    type: 'standard'
  }
];

export const RECOMMENDED_COMMUNITIES = [
  { id: '1', name: 'r/UIUXDesign', members: '2.1M members', category: 'palette' },
  { id: '2', name: 'r/Frontend', members: '850k members', category: 'code' },
  { id: '3', name: 'r/Minimalism', members: '1.4M members', category: 'architecture' }
];

export const TRENDING_TOPICS: TrendingTopic[] = [
  { id: '1', category: 'Trending in Technology', hashtag: '#GenerativeAI', postsCount: '45.2k posts' },
  { id: '2', category: 'Trending in Business', hashtag: '#RemoteWork2024', postsCount: '12.8k posts' },
  { id: '3', category: 'Trending in Design', hashtag: '#BentoGrids', postsCount: '8.4k posts' }
];

export const MOCK_COMMENTS = [
  {
    id: 'c1',
    author: { username: 'u/CodeMaster', avatar: '' },
    content: '非常赞同！模块化系统是未来的趋势，尤其是在空间利用率方面。',
    timestamp: '2小时前',
    votes: 45
  },
  {
    id: 'c2',
    author: { username: 'u/DesignLover', avatar: '' },
    content: '这些渲染图看起来太棒了。你使用的是什么软件？',
    timestamp: '1小时前',
    votes: 12
  },
  {
    id: 'c3',
    author: { username: 'u/TechGuru', avatar: '' },
    content: '我一直在关注这个项目，期待看到实物！',
    timestamp: '45分钟前',
    votes: 8
  }
];
